/*
 * RHQ Management Platform
 * Copyright (C) 2005-2008 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation, and/or the GNU Lesser
 * General Public License, version 2.1, also as published by the Free
 * Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License and the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and the GNU Lesser General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.rhq.core.domain.alert.composite;

import org.rhq.core.domain.alert.AlertCondition;
import org.rhq.core.domain.measurement.AvailabilityType;

/**
 * This composite serves both AVAILABILITY and AVAIL_DURATION alert categories as they both
 * require the same information from the composite.
 *  
 * @author Joseph Marques
 */
public class AlertConditionAvailabilityCategoryComposite extends AbstractAlertConditionCategoryComposite {

    private final Integer alertDefinitionId;
    private final Integer resourceId;
    private final AvailabilityType availabilityType;

    public AlertConditionAvailabilityCategoryComposite(AlertCondition condition, Integer alertDefinitionId,
        Integer resourceId,
        AvailabilityType availabilityType) {

        super(condition);

        this.alertDefinitionId = alertDefinitionId;
        this.resourceId = resourceId;
        // For a resource with no Availability records we initialize to UNKNOWN
        this.availabilityType = (null != availabilityType) ? availabilityType : AvailabilityType.UNKNOWN;
    }

    /**
     * @deprecated This constructor will NOT produce a valid instance of this class, because it cannot specify
     * the required alert definition id. Use {@link #AlertConditionAvailabilityCategoryComposite(
     * org.rhq.core.domain.alert.AlertCondition, Integer, Integer, org.rhq.core.domain.measurement.AvailabilityType)}
     * instead.
     */
    @Deprecated
    public AlertConditionAvailabilityCategoryComposite(AlertCondition condition, Integer resourceId, AvailabilityType availabilityType) {
        this(condition, null, resourceId, availabilityType);
    }

    public Integer getAlertDefinitionId() {
        return alertDefinitionId;
    }

    public Integer getResourceId() {
        return resourceId;
    }

    public AvailabilityType getAvailabilityType() {
        return availabilityType;
    }

}
