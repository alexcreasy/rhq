/*
 * RHQ Management Platform
 * Copyright (C) 2005-2013 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA
 */
package org.rhq.modules.plugins.jbossas7;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import org.rhq.core.domain.configuration.Configuration;
import org.rhq.core.domain.configuration.ConfigurationUpdateStatus;
import org.rhq.core.domain.configuration.PropertyList;
import org.rhq.core.domain.configuration.PropertySimple;
import org.rhq.core.domain.configuration.definition.ConfigurationDefinition;
import org.rhq.core.domain.measurement.MeasurementReport;
import org.rhq.core.domain.measurement.MeasurementScheduleRequest;
import org.rhq.core.domain.resource.CreateResourceStatus;
import org.rhq.core.pluginapi.configuration.ConfigurationUpdateReport;
import org.rhq.core.pluginapi.inventory.CreateResourceReport;
import org.rhq.core.pluginapi.inventory.ResourceComponent;
import org.rhq.core.pluginapi.measurement.MeasurementFacet;
import org.rhq.core.pluginapi.operation.OperationFacet;
import org.rhq.core.pluginapi.operation.OperationResult;
import org.rhq.modules.plugins.jbossas7.json.Address;
import org.rhq.modules.plugins.jbossas7.json.Operation;
import org.rhq.modules.plugins.jbossas7.json.Result;

/**
 * Component class for AS7 host and domain controllers.
 *
 * @author Heiko W. Rupp
 */
public class HostControllerComponent<T extends ResourceComponent<?>> extends BaseServerComponent<T>
        implements MeasurementFacet, OperationFacet {

    private static final String DOMAIN_CONFIG_TRAIT = "domain-config-file";
    private static final String HOST_CONFIG_TRAIT = "host-config-file";

    @Override
    protected AS7Mode getMode() {
        return AS7Mode.DOMAIN;
    }

    @Override
    public void getValues(MeasurementReport report, Set<MeasurementScheduleRequest> requests) throws Exception {
        Set<MeasurementScheduleRequest> leftovers = new HashSet<MeasurementScheduleRequest>(requests.size());
        for (MeasurementScheduleRequest request: requests) {
            String requestName = request.getName();
            if (requestName.equals(DOMAIN_CONFIG_TRAIT) || requestName.equals(HOST_CONFIG_TRAIT)) {
                collectConfigTrait(report, request);
            } else {
                leftovers.add(request); // handled below
            }
        }

        super.getValues(report, leftovers);
    }

    @Override
    public OperationResult invokeOperation(String name, Configuration parameters) throws InterruptedException,
        Exception {

        OperationResult operationResult;
        if (name.equals("start")) {
            operationResult = startServer();
        } else if (name.equals("restart")) {
            operationResult = restartServer(parameters);
        } else if (name.equals("executeCommands") || name.equals("executeScript")) {
            return runCliCommand(parameters);
        } else if (name.equals("shutdown")) {
            // This is a bit trickier, as it needs to be executed on the level on /host=xx
            String domainHost = pluginConfiguration.getSimpleValue("domainHost", "");
            if (domainHost.isEmpty()) {
                OperationResult result = new OperationResult();
                result.setErrorMessage("No domain host found - can not continue");
                operationResult = result;
            }
            Operation op = new Operation("shutdown", "host", domainHost);
            Result res = getASConnection().execute(op);
            operationResult = postProcessResult(name, res);

            if (waitUntilDown()) {
                operationResult.setSimpleResult("Success");
            } else {
                operationResult.setErrorMessage("Was not able to shut down the server.");
            }
        } else if (name.equals("installRhqUser")) {
            operationResult = installManagementUser(parameters, pluginConfiguration);
        } else {

            // Defer other stuff to the base component for now
            operationResult = super.invokeOperation(name, parameters);
        }

        context.getAvailabilityContext().requestAvailabilityCheck();

        return operationResult;
    }

    @Override
    public CreateResourceReport createResource(CreateResourceReport report) {

        // If Content is to be deployed, call the deployContent method
        if (report.getPackageDetails() != null)
            return super.deployContent(report);

        String targetTypeName = report.getResourceType().getName();
        Operation op;

        String resourceName = report.getUserSpecifiedResourceName();
        Configuration rc = report.getResourceConfiguration();
        Address targetAddress ;

        // Dispatch according to child type
        if (targetTypeName.equals("ServerGroup")) {
            targetAddress = new Address(); // Server groups are at / level
            targetAddress.add("server-group", resourceName);
            op = new Operation("add", targetAddress);


            String profile = rc.getSimpleValue("profile", "");
            if (profile.isEmpty()) {
                report.setErrorMessage("No profile given");
                report.setStatus(CreateResourceStatus.FAILURE);
                return report;
            }
            op.addAdditionalProperty("profile", profile);
            String socketBindingGroup = rc.getSimpleValue("socket-binding-group", "");
            if (socketBindingGroup.isEmpty()) {
                report.setErrorMessage("No socket-binding-group given");
                report.setStatus(CreateResourceStatus.FAILURE);
                return report;
            }
            op.addAdditionalProperty("socket-binding-group", socketBindingGroup);
            PropertySimple offset = rc.getSimple("socket-binding-port-offset");
            if (offset != null && offset.getStringValue() != null)
                op.addAdditionalProperty("socket-binding-port-offset", offset.getIntegerValue());

            PropertySimple jvm = rc.getSimple("jvm");
            if (jvm!=null) {
                op.addAdditionalProperty("jvm",jvm.getStringValue());
            }
        }
        else if (targetTypeName.equals(BaseComponent.MANAGED_SERVER)) {

            String targetHost = rc.getSimpleValue("hostname",null);
            if (targetHost==null) {
                report.setErrorMessage("No domain host given");
                report.setStatus(CreateResourceStatus.FAILURE);
                return report;
            }

            targetAddress = new Address("host",targetHost);
            targetAddress.add("server-config", resourceName);
            op = new Operation("add", targetAddress);
            String socketBindingGroup = rc.getSimpleValue("socket-binding-group", "");
            if (socketBindingGroup.isEmpty()) {
                report.setErrorMessage("No socket-binding-group given");
                report.setStatus(CreateResourceStatus.FAILURE);
                return report;
            }
            op.addAdditionalProperty("socket-binding-group", socketBindingGroup);
            String autostartS = rc.getSimpleValue("auto-start","false");
            boolean autoStart = Boolean.valueOf(autostartS);
            op.addAdditionalProperty("auto-start",autoStart);

            String portS = rc.getSimpleValue("socket-binding-port-offset","0");
            int portOffset = Integer.parseInt(portS);
            op.addAdditionalProperty("socket-binding-port-offset",portOffset);

            String serverGroup = rc.getSimpleValue("group",null);
            if (serverGroup==null) {
                report.setErrorMessage("No server group given");
                report.setStatus(CreateResourceStatus.FAILURE);
                return report;
            }
            op.addAdditionalProperty("group",serverGroup);

        } else if (targetTypeName.equals("JVM-Definition")) {
            return super.createResource(report);

        }
        else {
            throw new IllegalArgumentException("Don't know yet how to create instances of " + targetTypeName);
        }
        Result res = getASConnection().execute(op);
        if (res.isSuccess()) {
            if (targetTypeName.equals(BaseComponent.MANAGED_SERVER)) {
                report.setResourceKey(ManagedASDiscovery.createResourceKey(rc.getSimpleValue("hostname"),
                    report.getUserSpecifiedResourceName()));
            } else {
                report.setResourceKey(targetAddress.getPath());
            }
            report.setResourceName(resourceName);
            report.setStatus(CreateResourceStatus.SUCCESS);

            if (targetTypeName.equals("ServerGroup")) {
                PropertyList sysProperties = rc.getList("*2");
                if (sysProperties !=null && !sysProperties.getList().isEmpty()) {
                    // because AS7 does not allow us to pass system properties while creating server-group we must do it now
                    ConfigurationUpdateReport rep = new ConfigurationUpdateReport(rc);
                    ConfigurationDefinition configDef = report.getResourceType().getResourceConfigurationDefinition();
                    ConfigurationWriteDelegate delegate = new ConfigurationWriteDelegate(configDef, getASConnection(), targetAddress);
                    delegate.updateResourceConfiguration(rep);
                    if (ConfigurationUpdateStatus.FAILURE.equals(rep.getStatus())) {
                        report.setStatus(CreateResourceStatus.FAILURE);
                        report.setErrorMessage("Failed to additionally configure server group: "+rep.getErrorMessage());
                    }
                }
            }
        } else {
            report.setErrorMessage(res.getFailureDescription());
            report.setStatus(CreateResourceStatus.FAILURE);
            report.setException(res.getRhqThrowable());
        }
        return report;
    }

    @NotNull
    @Override
    protected Address getEnvironmentAddress() {
        return new Address("host=" + getHostName() + ",core-service=host-environment");
    }

    @NotNull
    @Override
    protected Address getHostAddress() {
        return new Address("host=" + getHostName());
    }

    private String getHostName() {
        return context.getPluginConfiguration().getSimpleValue("domainHost", "master");
    }

    @NotNull
    @Override
    protected String getBaseDirAttributeName() {
        return "domain-base-dir";
    }

}
