package com.hrgenius.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/** Leave category with annual entitlement. Mapped to LEAVE_TYPES (V1). */
@Entity
@Table(name = "LEAVE_TYPES")
@Getter
@Setter
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NAME", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "YEARLY_LIMIT", nullable = false)
    private int yearlyLimit;
}
