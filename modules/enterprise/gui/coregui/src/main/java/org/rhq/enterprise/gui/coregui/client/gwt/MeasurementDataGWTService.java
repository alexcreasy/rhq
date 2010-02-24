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
package org.rhq.enterprise.gui.coregui.client.gwt;

import org.rhq.core.domain.auth.Subject;
import org.rhq.core.domain.measurement.DisplayType;
import org.rhq.core.domain.measurement.MeasurementData;
import org.rhq.core.domain.measurement.MeasurementDataTrait;
import org.rhq.core.domain.measurement.composite.MeasurementDataNumericHighLowComposite;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

import java.util.List;
import java.util.Set;

/**
 * @author Greg Hinkle
 */
@RemoteServiceRelativePath("MeasurementDataGWTService")
public interface MeasurementDataGWTService extends RemoteService {


    List<MeasurementDataTrait> findCurrentTraitsForResource(int resourceId, DisplayType displayType);

    Set<MeasurementData> findLiveData(int resourceId, int[] definitionIds);

    List<List<MeasurementDataNumericHighLowComposite>> findDataForResource(int resourceId,
        int[] definitionIds, long beginTime, long endTime, int numPoints);


}
