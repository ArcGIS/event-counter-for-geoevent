package com.esri.geoevent.processor.eventcounter;

import com.esri.ges.core.component.ComponentException;
import com.esri.ges.messaging.Messaging;
import com.esri.ges.processor.GeoEventProcessor;
import com.esri.ges.processor.GeoEventProcessorServiceBase;

public class EventCounterService extends GeoEventProcessorServiceBase
{
  private Messaging messaging;

  public EventCounterService()
  {
    definition = new EventCounterDefinition();
  }

  @Override
  public GeoEventProcessor create() throws ComponentException
  {
    EventCounter detector = new EventCounter(definition);
    detector.setMessaging(messaging);
    return detector;
  }

  public void setMessaging(Messaging messaging)
  {
    this.messaging = messaging;
  }
}