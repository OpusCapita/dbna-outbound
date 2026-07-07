package com.opuscapita.dbna.common.container.metadata;

import com.google.gson.annotations.Since;

import java.io.Serializable;

public class ContainerValidationRule implements Serializable {

    private static final long serialVersionUID = -5680126394018616968L;

    @Since(1.0) private Integer id;
    @Since(1.0) private String description;
    @Since(1.0) private String archetype;
    @Since(1.0) private String localName;
    @Since(1.0) private String documentId;
    @Since(1.0) private String processId;
    @Since(1.0) private String processSchema;
    @Since(1.0) private String version;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getArchetype() {
        return archetype;
    }

    public void setArchetype(String archetype) {
        this.archetype = archetype;
    }

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getProcessSchema() {
        return processSchema;
    }

    public void setProcessSchema(String processSchema) {
        this.processSchema = processSchema;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ContainerValidationRule that = (ContainerValidationRule) o;

        if (documentId != null && processId != null) {
            return documentId.equals(that.documentId) && processId.equals(that.processId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = documentId.hashCode();
        result = 31 * result + (processId != null ? processId.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return String.format("ValidationRule {id: %s, name: %s}", id, description);
    }
}

