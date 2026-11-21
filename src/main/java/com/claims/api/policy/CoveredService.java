package com.claims.api.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catalog of service codes (e.g. CPT codes) and whether the plan covers them. */
@Entity
@Table(name = "covered_service")
public class CoveredService {

    @Id
    @Column(name = "service_code")
    private String serviceCode;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private boolean covered;

    protected CoveredService() {
        // for JPA
    }

    public CoveredService(String serviceCode, String description, boolean covered) {
        this.serviceCode = serviceCode;
        this.description = description;
        this.covered = covered;
    }

    public void update(String description, boolean covered) {
        this.description = description;
        this.covered = covered;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public String getDescription() {
        return description;
    }

    public boolean isCovered() {
        return covered;
    }
}
