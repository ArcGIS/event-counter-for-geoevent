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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.geoevent.FieldException;
import com.esri.ges.core.geoevent.GeoEvent;
import com.esri.ges.core.geoevent.GeoEventCache;
import com.esri.ges.core.geoevent.GeoEventDefinition;
import com.esri.ges.core.geoevent.GeoEventPropertyName;
import com.esri.ges.core.validation.ValidationException;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.messaging.EventDestination;
import com.esri.ges.messaging.EventUpdatable;
import com.esri.ges.messaging.GeoEventCreator;
import com.esri.ges.messaging.GeoEventProducer;
import com.esri.ges.messaging.Messaging;
import com.esri.ges.messaging.MessagingException;
import com.esri.ges.processor.CacheEnabledGeoEventProcessor;
import com.esri.ges.processor.GeoEventProcessorDefinition;
import com.esri.ges.util.Converter;
import com.esri.ges.util.Validator;

public class EventCounter extends CacheEnabledGeoEventProcessor implements Observer, GeoEventProducer, EventUpdatable
{
	private static final BundleLogger							LOGGER				= BundleLoggerFactory.getLogger(EventCounter.class);

	private EventCountNotificationMode           notificationMode;
  private long                                 reportInterval;
  private final Map<String, EventCountMonitor> eventCountMonitors       = new ConcurrentHashMap<String, EventCountMonitor>();
  private final Map<String, Thread>            eventCountMonitorThreads = new ConcurrentHashMap<String, Thread>();
  private Messaging                            messaging;
  private GeoEventCreator                      geoEventCreator;
  private GeoEventProducer                     geoEventProducer;
  private boolean                              autoResetCounter;
  private Date                                 resetTime;
  private boolean                              clearCache;
  private Timer                                clearCacheTimer;
	private boolean																isInitialized	= false;

  class ClearCacheTask extends TimerTask
  {
    public void run()
    {
      if (autoResetCounter == true)
      {
        for (EventCountMonitor monitor : eventCountMonitors.values())
        {
          monitor.setEventCount(0);
        }
      }
      // clear the cache
      if (clearCache == true)
      {
        for (EventCountMonitor monitor : eventCountMonitors.values())
        {
          monitor.stop();
          monitor.stopMonitoring();
        }
        eventCountMonitors.clear();
        eventCountMonitorThreads.clear();
      }
    }
  }

  protected EventCounter(GeoEventProcessorDefinition definition) throws ComponentException
  {
    super(definition);
  }

  public void afterPropertiesSet()
  {
  	if (!isInitialized)
		{
	    notificationMode = Validator.valueOfIgnoreCase(EventCountNotificationMode.class, getProperty("notificationMode").getValueAsString(), EventCountNotificationMode.OnChange);
	    reportInterval = Converter.convertToInteger(getProperty("reportInterval").getValueAsString(), 10) * 1000;
	    autoResetCounter = Converter.convertToBoolean(getProperty("autoResetCounter").getValueAsString());
	    String[] resetTimeStr = getProperty("resetTime").getValueAsString().split(":");

	    // Get the Date corresponding to 11:01:00 pm today.
	    Calendar calendar = Calendar.getInstance();

	    calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(resetTimeStr[0]));
	    calendar.set(Calendar.MINUTE, Integer.parseInt(resetTimeStr[1]));
	    calendar.set(Calendar.SECOND, Integer.parseInt(resetTimeStr[2]));
	    resetTime = calendar.getTime();
	    clearCache = Converter.convertToBoolean(getProperty("clearCache").getValueAsString());
	    isInitialized = true;
		}
  }

  @Override
  public void setId(String id)
  {
    super.setId(id);
		geoEventProducer = messaging.createGeoEventProducer(new EventDestination(id + ":event"));
  }

  @Override
  public GeoEvent process(GeoEvent geoEvent) throws Exception
  {
    startMonitoring(geoEvent);
    return null;
  }

  @Override
	public List<EventDestination> getEventDestinations()
	{
		return (geoEventProducer != null) ? Arrays.asList(geoEventProducer.getEventDestination()) : new ArrayList<EventDestination>();
	}

  @Override
  public void validate() throws ValidationException
  {
    super.validate();
    List<String> errors = new ArrayList<String>();
    if (reportInterval <= 0)
      errors.add(LOGGER.translate("VALIDATION_INVALID_REPORT_INTERVAL", definition.getName()));
    if (errors.size() > 0)
    {
      StringBuffer sb = new StringBuffer();
      for (String message : errors)
        sb.append(message).append("\n");
      throw new ValidationException(LOGGER.translate("VALIDATION_ERROR", this.getClass().getName(), sb.toString()));
    }
  }

  @Override
  public void update(Observable observable, Object event)
  {
    if (event instanceof EventCountEvent)
    {
      EventCountEvent gapEvent = (EventCountEvent) event;
      if (gapEvent.isStopMonitoring())
        stopMonitoring(gapEvent.getGeoEvent());
      else
      {
        try
        {
          send(createEventCounterGeoEvent(gapEvent));
        }
        catch (MessagingException error)
        {
        	LOGGER.error("SEND_REPORT_ERROR", gapEvent, error.getMessage());
  				LOGGER.info(error.getMessage(), error);
        }
      }
    }
    notifyObservers(event);
  }

  @Override
  public void onServiceStart()
  {
    if (this.autoResetCounter == true || this.clearCache == true)
    {
      if (clearCacheTimer == null)
      {
        // Get the Date corresponding to 11:01:00 pm today.
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTime(resetTime);
        Date time1 = calendar1.getTime();

        clearCacheTimer = new Timer();
        Long dayInMilliSeconds = 60 * 60 * 24 * 1000L;
        clearCacheTimer.scheduleAtFixedRate(new ClearCacheTask(), time1, dayInMilliSeconds);
      }
    }

    for (EventCountMonitor monitor : eventCountMonitors.values())
      monitor.start();
  }

  @Override
  public void onServiceStop()
  {
    for (EventCountMonitor monitor : eventCountMonitors.values())
      monitor.stop();

    if (clearCacheTimer != null)
    {
      clearCacheTimer.cancel();
    }
  }

  @Override
  public void shutdown()
  {
    super.shutdown();
    for (EventCountMonitor monitor : eventCountMonitors.values())
    {
      monitor.stop();
      monitor.stopMonitoring();
    }
    eventCountMonitors.clear();
    eventCountMonitorThreads.clear();

    if (clearCacheTimer != null)
    {
      clearCacheTimer.cancel();
    }
  }

  @Override
  public boolean isCacheRequired()
  {
    return true;
  }

  @Override
  public EventDestination getEventDestination()
  {
  	return (geoEventProducer != null) ? geoEventProducer.getEventDestination() : null;
  }

  @Override
  public void send(GeoEvent geoEvent) throws MessagingException
  {
    if (geoEventProducer != null && geoEvent != null)
      geoEventProducer.send(geoEvent);
  }

  private String buildCacheKey(GeoEvent geoEvent)
  {
    if (geoEvent != null && geoEvent.getTrackId() != null)
    {
      GeoEventDefinition definition = geoEvent.getGeoEventDefinition();
      return definition.getOwner() + "/" + definition.getName() + "/" + geoEvent.getTrackId();
    }
    return null;
  }

  private void startMonitoring(GeoEvent geoEvent)
  {
    String id = buildCacheKey(geoEvent);
    if (id != null)
    {
      EventCountMonitor monitor = null;
      if (eventCountMonitors.containsKey(id))
        monitor = eventCountMonitors.get(id);
      else
      {
        monitor = new EventCountMonitor(geoEventCache, geoEvent, notificationMode, reportInterval, autoResetCounter, resetTime);
        monitor.addObserver(this);
        eventCountMonitors.put(id, monitor);
        eventCountMonitorThreads.put(id, new Thread(monitor, id));
      }
      if (monitor != null && !monitor.isMonitoring())
        eventCountMonitorThreads.get(id).start();
    }
  }

  private void stopMonitoring(GeoEvent geoEvent)
  {
    String id = buildCacheKey(geoEvent);
    if (id != null && eventCountMonitors.containsKey(id))
    {
      eventCountMonitors.remove(id).stopMonitoring();
      eventCountMonitorThreads.remove(id).interrupt();
    }
  }

  private GeoEvent createEventCounterGeoEvent(EventCountEvent event) throws MessagingException
  {
    GeoEvent gapEvent = null;
    if (geoEventCreator != null)
    {
      try
      {
        GeoEvent geoEvent = event.getGeoEvent();
        gapEvent = geoEventCreator.create("EventCount", definition.getUri().toString());
        gapEvent.setField(0, geoEvent.getTrackId());
        gapEvent.setField(1, event.getEventCount());
        gapEvent.setField(2, ((Date) geoEvent.getProperty(GeoEventPropertyName.RECEIVED_TIME)));
        gapEvent.setField(3, geoEvent.getGeometry(getProperty("geometryField").getValueAsString()));
        gapEvent.setProperty(GeoEventPropertyName.TYPE, "event");
        gapEvent.setProperty(GeoEventPropertyName.OWNER_ID, getId());
        gapEvent.setProperty(GeoEventPropertyName.OWNER_URI, definition.getUri());
      }
      catch (FieldException error)
      {
        gapEvent = null;
        LOGGER.error("CREATE_GEOEVENT_FAILED", error.getMessage());
				LOGGER.info(error.getMessage(), error);
      }
    }
    return gapEvent;
  }

  public void setMessaging(Messaging messaging)
  {
  	this.messaging = messaging;
		geoEventCreator = messaging.createGeoEventCreator();
  }
  
  @Override
	public void disconnect()
	{
		if (geoEventProducer != null)
			geoEventProducer.disconnect();
	}

	@Override
	public String getStatusDetails()
	{
		return (geoEventProducer != null) ? geoEventProducer.getStatusDetails() : "";
	}

	@Override
	public void init() throws MessagingException
	{
		afterPropertiesSet();
	}

	@Override
	public boolean isConnected()
	{
		return (geoEventProducer != null) ? geoEventProducer.isConnected() : false;
	}

	@Override
	public void setup() throws MessagingException
	{
		;
	}
}

final class EventCountMonitor extends Observable implements Runnable
{
  private boolean                    monitoring;
  private boolean                    running;
  private GeoEventCache              geoEventCache;
  private GeoEvent                   geoEvent;
  private EventCountNotificationMode notificationMode;
  private long                       reportInterval;
  private long                       eventCount;
  private boolean                    autoResetCounter;
  private Date                       resetTime;
  private boolean                    changeDetected = false;

  protected EventCountMonitor(GeoEventCache geoEventCache, GeoEvent geoEvent, EventCountNotificationMode notificationMode, long reportInterval, boolean autoResetCounter, Date resetTime)
  {
    this.geoEventCache = geoEventCache;
    this.geoEvent = geoEventCache.getLastGeoEvent(geoEvent);
    this.eventCount = 0;
    this.monitoring = false;
    this.running = true;
    setNotificationMode(notificationMode);
    setTimeInterval(reportInterval);
    setAutoResetCounter(autoResetCounter);
    setResetTime(resetTime);
  }

  public EventCountNotificationMode getNotificationMode()
  {
    return notificationMode;
  }

  public void setNotificationMode(EventCountNotificationMode notificationMode)
  {
    this.notificationMode = (notificationMode != null) ? notificationMode : EventCountNotificationMode.OnChange;
  }

  public long getTimeInterval()
  {
    return reportInterval;
  }

  public void setTimeInterval(long timeInterval)
  {
    this.reportInterval = (timeInterval > 0) ? timeInterval : 120000;
  }

  public long getEventCount()
  {
    return eventCount;
  }

  public void setEventCount(long eventCount)
  {
    if (this.eventCount != eventCount)
    {
      this.eventCount = eventCount;
      this.changeDetected = true;
    }
    else
    {
      this.changeDetected = false;
    }

  }

  @Override
  public void run()
  {
    monitoring = true;
    while (monitoring)
    {
      try
      {
        if (running)
        {
          GeoEvent geoEvent = geoEventCache.getLastGeoEvent(this.geoEvent);
          if (geoEvent != null)
          {
            this.eventCount++;
            consoleDebugPrintLn(geoEvent.getTrackId() + ":" + this.eventCount);
            switch (notificationMode)
            {
              case OnChange:
                if (this.changeDetected == true)
                {
                  //Thread.sleep(1);
                  notifyObservers(new EventCountEvent(geoEvent, this.eventCount, false));
                }
                break;
              case Continuous:
                Thread.sleep(reportInterval);
                notifyObservers(new EventCountEvent(geoEvent, this.eventCount, false));
                break;
            }
            // reset the changeDetected flag
            this.changeDetected = false;
            this.geoEvent = geoEvent;
          }
          else
            notifyObservers(new EventCountEvent(this.geoEvent, 0, true));
        }
      }
      catch (InterruptedException e)
      {
        stopMonitoring();
      }
    }
  }

  public boolean isMonitoring()
  {
    return monitoring;
  }

  public void stopMonitoring()
  {
    monitoring = false;
  }

  public void start()
  {
    running = true;
  }

  public void stop()
  {
    running = false;
  }

  @Override
  public void notifyObservers(Object event)
  {
    if (event != null)
    {
      setChanged();
      super.notifyObservers(event);
      clearChanged();
    }
  }

  public static void consoleDebugPrintLn(String msg)
  {
    String consoleOut = System.getenv("GEP_CONSOLE_OUTPUT");
    if (consoleOut != null && "1".equals(consoleOut))
    {
      System.out.println(msg);
    }
  }

  public static void consoleDebugPrint(String msg)
  {
    String consoleOut = System.getenv("GEP_CONSOLE_OUTPUT");
    if (consoleOut != null && "1".equals(consoleOut))
    {
      System.out.print(msg);
    }
  }

  public boolean isAutoResetCounter()
  {
    return autoResetCounter;
  }

  public void setAutoResetCounter(boolean autoResetCounter)
  {
    this.autoResetCounter = autoResetCounter;
  }

  public Date getResetTime()
  {
    return resetTime;
  }

  public void setResetTime(Date resetTime2)
  {
    this.resetTime = resetTime2;
  }

}
