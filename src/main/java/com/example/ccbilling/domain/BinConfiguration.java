package com.example.ccbilling.domain;

import com.example.ccbilling.billing.BillingRules;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "bin_configurations")
public class BinConfiguration extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bin_code", nullable = false, length = 6, updatable = false)
    private String binCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Embedded
    private RuleSet rules;

    /** Incremented whenever any billing rule value changes. */
    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;

    @Version
    private Integer version;

    protected BinConfiguration() {
    }

    public BinConfiguration(String binCode, String name, BillingRules rules) {
        this.binCode = binCode;
        this.name = name;
        this.rules = new RuleSet(rules);
        this.ruleVersion = 1;
    }

    /** Applies new values; the rule version only advances when a rule value actually changed. */
    public boolean update(String name, BillingRules newRules) {
        this.name = name;
        if (toRules().sameAs(newRules)) {
            return false;
        }
        this.rules = new RuleSet(newRules);
        this.ruleVersion++;
        return true;
    }

    public BillingRules toRules() { return rules.toRules(); }

    public RuleSet getRuleSet() { return rules; }

    public Long getId() { return id; }

    public String getBinCode() { return binCode; }

    public String getName() { return name; }

    public int getRuleVersion() { return ruleVersion; }
}
