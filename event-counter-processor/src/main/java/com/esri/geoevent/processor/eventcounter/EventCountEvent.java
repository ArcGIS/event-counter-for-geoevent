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