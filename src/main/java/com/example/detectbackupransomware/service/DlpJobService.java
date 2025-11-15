package com.example.detectbackupransomware.service;

import com.example.detectbackupransomware.entity.SensitiveDataModel;
import com.example.detectbackupransomware.repository.SensitiveDataModelRepository;
import com.google.cloud.dlp.v2.DlpServiceClient;
import com.google.privacy.dlp.v2.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio para crear y ejecutar Cloud DLP Jobs para inspeccionar backups en Cloud Storage.
 * Detecta si los datos sensibles (emails, datos bancarios, etc.) están encriptados.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DlpJobService {

    private final SensitiveDataModelRepository sensitiveDataModelRepository;

    @Value("${gcp.dlp.project-id:${GOOGLE_CLOUD_PROJECT}}")
    private String projectId;

    @Value("${gcp.storage.backup-bucket:backup-bucket}")
    private String backupBucket;

    /**
     * Crea un DLP Job para inspeccionar un backup en Cloud Storage y detectar si datos sensibles están encriptados.
     */
    public String createDlpInspectionJob(String backupLocation, String jobId) {
        if (projectId == null || projectId.isEmpty()) {
            log.warn("Cloud DLP no está habilitado (project-id no configurado)");
            return null;
        }

        log.info("Creando DLP Job para inspeccionar backup [BackupLocation: {}] [JobID: {}]", 
                backupLocation, jobId);

        try (DlpServiceClient dlpClient = DlpServiceClient.create()) {
            // Obtener modelos de datos sensibles activos
            List<SensitiveDataModel> sensitiveDataModels = sensitiveDataModelRepository.findByActiveTrue();

            if (sensitiveDataModels.isEmpty()) {
                log.warn("No hay modelos de datos sensibles configurados");
                return null;
            }

            // Construir InfoTypes basados en los modelos de datos sensibles
            List<InfoType> infoTypes = buildInfoTypes(sensitiveDataModels);

            // Crear InspectConfig
            InspectConfig inspectConfig = InspectConfig.newBuilder()
                    .addAllInfoTypes(infoTypes)
                    .setMinLikelihood(Likelihood.POSSIBLE)
                    .setIncludeQuote(true)
                    .build();

            // Configurar Cloud Storage como fuente
            CloudStorageOptions.CloudStorageFileSet fileSet = CloudStorageOptions.CloudStorageFileSet.newBuilder()
                    .setUrl(backupLocation)
                    .build();

            CloudStorageOptions cloudStorageOptions = CloudStorageOptions.newBuilder()
                    .setFileSet(fileSet)
                    .build();

            StorageConfig storageConfig = StorageConfig.newBuilder()
                    .setCloudStorageOptions(cloudStorageOptions)
                    .build();

            // Configurar destino de resultados (opcional: puede guardarse en BigQuery o Cloud Storage)
            // Por ahora, solo retornamos el resultado en el job
            InspectJobConfig inspectJobConfig = InspectJobConfig.newBuilder()
                    .setStorageConfig(storageConfig)
                    .setInspectConfig(inspectConfig)
                    .setInspectTemplateName("")  // Opcional: usar un template predefinido
                    .build();

            // Crear el Job
            CreateDlpJobRequest createDlpJobRequest = CreateDlpJobRequest.newBuilder()
                    .setParent(ProjectName.of(projectId).toString())
                    .setInspectJob(inspectJobConfig)
                    .setJobId(jobId)
                    .build();

            DlpJob dlpJob = dlpClient.createDlpJob(createDlpJobRequest);

            log.info("DLP Job creado exitosamente [JobName: {}]", dlpJob.getName());

            return dlpJob.getName();

        } catch (Exception e) {
            log.error("Error al crear DLP Job: {}", e.getMessage(), e);
            throw new RuntimeException("Error al crear DLP Job: " + e.getMessage(), e);
        }
    }

    /**
     * Verifica el estado de un DLP Job y obtiene los resultados.
     * Retorna true si se detectaron datos sensibles encriptados.
     */
    public boolean checkDlpJobResults(String jobName) {
        if (jobName == null || jobName.isEmpty()) {
            return false;
        }

        log.info("Verificando resultados del DLP Job [JobName: {}]", jobName);

        try (DlpServiceClient dlpClient = DlpServiceClient.create()) {
            GetDlpJobRequest getJobRequest = GetDlpJobRequest.newBuilder()
                    .setName(jobName)
                    .build();

            DlpJob dlpJob = dlpClient.getDlpJob(getJobRequest);

            // Verificar estado del job
            if (dlpJob.getState() != DlpJob.JobState.DONE) {
                log.info("DLP Job aún en proceso [State: {}]", dlpJob.getState());
                return false;
            }

            // Obtener resultados
            InspectDataSourceDetails details = dlpJob.getInspectDetails();
            if (details == null) {
                log.warn("No se encontraron detalles de inspección en el DLP Job");
                return false;
            }

            // Verificar si se encontraron hallazgos
            int findingsCount = details.getResult().getFindingsCount();
            log.info("DLP Job completado [Findings: {}]", findingsCount);

            // Si hay hallazgos, podría indicar que los datos están encriptados o comprometidos
            // Analizar los hallazgos para determinar si son sospechosos
            return analyzeFindingsForEncryption(details.getResult());

        } catch (Exception e) {
            log.error("Error al verificar DLP Job: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Analiza los hallazgos del DLP para determinar si indican encriptación sospechosa.
     */
    private boolean analyzeFindingsForEncryption(InspectDataSourceDetails.Result result) {
        if (result.getFindingsCount() == 0) {
            return false;
        }

        // Si los datos sensibles no se detectaron (esperábamos encontrarlos pero no están),
        // podría indicar que están encriptados
        // O si se detectan patrones de encriptación, es sospechoso

        for (Finding finding : result.getFindingsList()) {
            String infoType = finding.getInfoType().getName();
            String quote = finding.getQuote();

            log.debug("Finding [InfoType: {}] [Quote: {}]", infoType, quote);

            // Verificar si el quote parece encriptado (base64, hexadecimal, caracteres no legibles)
            if (isQuoteEncrypted(quote)) {
                log.warn("⚠️ Datos sensibles parecen estar encriptados [InfoType: {}] [Quote: {}]", 
                        infoType, quote);
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica si un quote parece estar encriptado (base64, hex, caracteres no legibles).
     */
    private boolean isQuoteEncrypted(String quote) {
        if (quote == null || quote.length() < 10) {
            return false;
        }

        // Verificar si es base64 (contiene solo A-Z, a-z, 0-9, +, /, =)
        if (quote.matches("^[A-Za-z0-9+/=]+$") && quote.length() > 20) {
            return true;
        }

        // Verificar si es hexadecimal
        if (quote.matches("^[0-9a-fA-F]+$") && quote.length() > 32) {
            return true;
        }

        // Verificar si contiene muchos caracteres no imprimibles
        long nonPrintableCount = quote.chars()
                .filter(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && c < 32)
                .count();

        if (nonPrintableCount > quote.length() * 0.3) {
            return true;
        }

        return false;
    }

    /**
     * Construye InfoTypes basados en los modelos de datos sensibles.
     */
    private List<InfoType> buildInfoTypes(List<SensitiveDataModel> models) {
        List<InfoType> infoTypes = new ArrayList<>();

        for (SensitiveDataModel model : models) {
            String infoTypeName = model.getDlpInfoType() != null ? 
                    model.getDlpInfoType() : mapDataTypeToInfoType(model.getDataType());

            if (infoTypeName != null && !infoTypeName.isEmpty()) {
                infoTypes.add(InfoType.newBuilder().setName(infoTypeName).build());
            }
        }

        // Si no hay infoTypes específicos, usar los estándar de DLP
        if (infoTypes.isEmpty()) {
            infoTypes.add(InfoType.newBuilder().setName("EMAIL_ADDRESS").build());
            infoTypes.add(InfoType.newBuilder().setName("CREDIT_CARD_NUMBER").build());
            infoTypes.add(InfoType.newBuilder().setName("PHONE_NUMBER").build());
        }

        log.info("InfoTypes configurados para DLP: {}", 
                infoTypes.stream().map(InfoType::getName).collect(Collectors.toList()));

        return infoTypes;
    }

    /**
     * Mapea SensitiveDataType a InfoType estándar de DLP.
     */
    private String mapDataTypeToInfoType(SensitiveDataModel.SensitiveDataType dataType) {
        switch (dataType) {
            case EMAIL:
                return "EMAIL_ADDRESS";
            case PHONE:
                return "PHONE_NUMBER";
            case CREDIT_CARD:
                return "CREDIT_CARD_NUMBER";
            case SSN:
                return "US_SOCIAL_SECURITY_NUMBER";
            case PASSPORT:
                return "PASSPORT";
            case DRIVER_LICENSE:
                return "US_DRIVERS_LICENSE_NUMBER";
            case IP_ADDRESS:
                return "IP_ADDRESS";
            case DATE_OF_BIRTH:
                return "DATE_OF_BIRTH";
            case ADDRESS:
                return "STREET_ADDRESS";
            default:
                return null;
        }
    }
}

