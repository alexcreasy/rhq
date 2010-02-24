/*
 * RHQ Management Platform
 * Copyright (C) 2005-2010 Red Hat, Inc.
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
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.enterprise.gui.coregui.client.inventory.resource.detail;

import org.rhq.core.domain.resource.Resource;
import org.rhq.core.domain.resource.ResourceType;
import org.rhq.enterprise.gui.coregui.client.Presenter;
import org.rhq.enterprise.gui.coregui.client.components.FullHTMLPane;
import org.rhq.enterprise.gui.coregui.client.components.SubTabLayout;
import org.rhq.enterprise.gui.coregui.client.components.configuration.ConfigurationEditor;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.ResourceSelectListener;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.detail.monitoring.GraphListView;
import org.rhq.enterprise.gui.coregui.client.inventory.resource.type.ResourceTypeRepository;
import org.rhq.enterprise.gui.coregui.client.places.Place;

import com.smartgwt.client.types.ContentsType;
import com.smartgwt.client.types.Side;
import com.smartgwt.client.widgets.HTMLFlow;
import com.smartgwt.client.widgets.HTMLPane;
import com.smartgwt.client.widgets.layout.VLayout;
import com.smartgwt.client.widgets.tab.Tab;
import com.smartgwt.client.widgets.tab.TabSet;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

/**
 * Right panel of the resource view.
 *
 * @author Greg Hinkle
 */
public class ResourceDetailView extends VLayout implements Presenter, ResourceSelectListener {

    private Resource resource;

    private ResourceSummaryView summaryView;

    private Tab summaryTab;
    private Tab monitoringTab;
    private Tab inventoryTab;
    private Tab operationsTab;
    private Tab alertsTab;
    private Tab configurationTab;
    private Tab eventsTab;
    private Tab contentTab;
    private TabSet topTabSet;


    HTMLFlow title = new HTMLFlow();

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    @Override
    protected void onInit() {
        super.onInit();

        setWidth100();
        setHeight100();

        // addMember(new ResourceSummaryView());
        summaryView = new ResourceSummaryView();

        topTabSet = new TabSet();
        topTabSet.setTabBarPosition(Side.TOP);
        topTabSet.setWidth100();
        topTabSet.setHeight100();
        topTabSet.setEdgeMarginSize(0);
        topTabSet.setEdgeSize(0);


        summaryTab = new Tab("Summary", "/images/icons/Service_up_16.png");
        monitoringTab = new Tab("Monitoring", "/images/icons/Monitor_grey_16.png");
        inventoryTab = new Tab("Inventory", "/images/icons/Inventory_grey_16.png");
        operationsTab = new Tab("Operations", "/images/icons/Operation_grey_16.png");
        alertsTab = new Tab("Alerts", "/images/icons/Alert_grey_16.png");
        configurationTab = new Tab("Configuration", "/images/icons/Configure_grey_16.png");
        eventsTab = new Tab("Events", "/images/icons/Events_grey_16.png");
        contentTab = new Tab("Content", "/images/icons/Content_grey_16.png");


        topTabSet.setTabs(summaryTab, monitoringTab, inventoryTab, operationsTab, alertsTab, configurationTab, eventsTab, contentTab);

        title.setContents("Loading...");
        addMember(title);

        addMember(summaryView);

        addMember(topTabSet);

//        CoreGUI.addBreadCrumb(getPlace());
    }


    public boolean fireDisplay(Place place, List<Place> children) {
        if (place.equals(getPlace())) {


        }
        return true;

    }

    public Place getPlace() {
        return new Place(String.valueOf(resource.getId()), resource.getName());
    }

    public void onResourceSelected(Resource resource) {

        this.resource = resource;

        this.summaryView.onResourceSelected(resource);

        title.setContents("<h2>" + resource.getName() + "</h2>");
        title.markForRedraw();

        int selectedTab = topTabSet.getSelectedTabNumber();


        FullHTMLPane summaryPane = new FullHTMLPane("/rhq/resource/summary/overview-plain.xhtml?id=" + resource.getId());
        FullHTMLPane timelinePane = new FullHTMLPane("/rhq/resource/summary/timeline-plain.xhtml?id=" + resource.getId());
        SubTabLayout summarySet = new SubTabLayout();
        summarySet.registerSubTab("Overview", summaryPane);
        summarySet.registerSubTab("Timeline", timelinePane);

        topTabSet.updateTab(summaryTab, summarySet);





        SubTabLayout monitoringSet = new SubTabLayout();
        monitoringSet.registerSubTab("Graphs", new GraphListView(resource)); // new FullHTMLPane("/rhq/common/monitor/graphs.xhtml?id=" + resource.getId()));
        monitoringSet.registerSubTab("Tables", new FullHTMLPane("/rhq/common/monitor/tables.xhtml?id=" + resource.getId()));
        monitoringSet.registerSubTab("Traits", new FullHTMLPane("/rhq/resource/monitor/traits.xhtml?id=" + resource.getId()));
        monitoringSet.registerSubTab("Availability", new FullHTMLPane("/rhq/resource/monitor/availabilityHistory.xhtml?id=" + resource.getId()));
        monitoringSet.registerSubTab("Schedules", new FullHTMLPane("/rhq/resource/monitor/schedules.xhtml?id=" + resource.getId()));
        topTabSet.updateTab(monitoringTab, monitoringSet);


        topTabSet.updateTab(configurationTab, new ConfigurationEditor(resource.getId(), resource.getResourceType().getId()));

        topTabSet.setSelectedTab(selectedTab);

        updateTabStatus();

        topTabSet.markForRedraw();
    }


    private void updateTabStatus() {
        ResourceTypeRepository.Cache.getInstance().getResourceTypes(resource.getResourceType().getId(),
                EnumSet.of(ResourceTypeRepository.MetadataType.content, ResourceTypeRepository.MetadataType.operations, ResourceTypeRepository.MetadataType.events, ResourceTypeRepository.MetadataType.resourceConfigurationDefinition),
                new ResourceTypeRepository.TypeLoadedCallback() {
                    public void onTypesLoaded(ResourceType type) {


                        if (type.getOperationDefinitions() == null || type.getOperationDefinitions().isEmpty()) {
                            topTabSet.disableTab(operationsTab);
                        } else {
                            topTabSet.enableTab(operationsTab);
                        }

                        if (type.getEventDefinitions() == null || type.getEventDefinitions().isEmpty()) {
                            topTabSet.disableTab(eventsTab);
                        } else {
                            topTabSet.enableTab(eventsTab);
                        }

                        if (type.getPackageTypes() == null || type.getPackageTypes().isEmpty())  {
                            topTabSet.disableTab(contentTab);
                        } else {
                            topTabSet.enableTab(contentTab);
                        }

                        if (type.getResourceConfigurationDefinition() == null) {
                            topTabSet.disableTab(configurationTab);
                        } else {
                            topTabSet.enableTab(configurationTab);
                        }

                        if (topTabSet.getSelectedTab().getDisabled()) {
                            topTabSet.setSelectedTab(0);
                        }

                    }
                });


    }

}
