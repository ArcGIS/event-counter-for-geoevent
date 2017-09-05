/*
  Copyright 2017 Esri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.​

  For additional information, contact:
  Environmental Systems Research Institute, Inc.
  Attn: Contracts Dept
  380 New York Street
  Redlands, California, USA 92373

  email: contracts@esri.com
*/

package com.esri.geoevent.processor.eventcounter;

import com.esri.ges.core.geoevent.GeoEvent;

public class EventCountEvent
{
	private GeoEvent geoEvent;
	private long eventCount;
	private boolean stopMonitoring;

	public EventCountEvent(GeoEvent geoEvent, long eventCount, boolean stopMonitoring)
	{
		this.geoEvent = geoEvent;
		this.eventCount = eventCount;
		this.stopMonitoring = stopMonitoring;
	}

	public GeoEvent getGeoEvent()
	{
		return geoEvent;
	}

	public long getEventCount()
	{
		return eventCount;
	}

	public boolean isStopMonitoring()
	{
		return stopMonitoring;
	}

	@Override
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		sb.append("EventCountEvent(");
		sb.append(geoEvent.getTrackId());
		sb.append(", ");
		sb.append(eventCount);
		sb.append(")");
		return sb.toString();
	}
}