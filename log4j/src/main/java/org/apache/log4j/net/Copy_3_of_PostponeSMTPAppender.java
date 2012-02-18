/* Project: log4j
 * File: PostponeSMTPAppender.java
 * Created on Dec 3, 2011
 *  
 * Author: Sergiy Goncharenko
 * Copyright 2011, Edifecs Inc.
 */
package org.apache.log4j.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Layout;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.OptionHandler;
import org.apache.log4j.spi.TriggeringEventEvaluator;

/**
 * <p>Title: PostponeSMTPAppender.java </p>
 * <p>Description:  </p>
 * Typical usage:
 *
 * <p>Copyright (c) 2011</p>
 * <p>Company: Edifecs Inc.</p>
 * $Date: $
 * @version $Revision: $
 * $Header: MySMTPAppender.java, Dec 3, 2011 11:53:39 PM SergeyG Exp $
 */
public class Copy_3_of_PostponeSMTPAppender extends SMTPAppender
{
    /*
     * postpone - a time in milliseconds to postpone sending
     * if postpone is equals -1 the message will send immediately
     */
    private long postpone = -1;

    public Copy_3_of_PostponeSMTPAppender()
    {
        this(new CompoundPostponeEvaluator(new DefaultEvaluator()));
    }

    public Copy_3_of_PostponeSMTPAppender(CompoundPostponeEvaluator compoundPostponeEvaluator)
    {
        super(compoundPostponeEvaluator);
        compoundPostponeEvaluator.setSMTPAppender(this);
        setEvaluator(compoundPostponeEvaluator);
    }

    public void setEvaluatorClass(String value)
    {
        if (!CompoundPostponeEvaluator.class.getClass().getName().equals(value))
        {
            CompoundPostponeEvaluator compoundPostponeEvaluator = new CompoundPostponeEvaluator((TriggeringEventEvaluator) OptionConverter.instantiateByClassName(value, TriggeringEventEvaluator.class, evaluator));
            compoundPostponeEvaluator.setSMTPAppender(this);
            setEvaluator(compoundPostponeEvaluator);
        }
        else
        {
            super.setEvaluatorClass(value);
        }
    }

    @Override
    protected boolean checkEntryConditions()
    {
        boolean checkEntryConditions = super.checkEntryConditions();
        if (checkEntryConditions)
        {
            checkEvaluator();
        }
        return checkEntryConditions;
    }

    protected CompoundPostponeEvaluator checkEvaluator()
    {
        return (CompoundPostponeEvaluator) getEvaluator();
    }

    @Override
    protected synchronized String formatBody()
    {
        CompoundPostponeEvaluator compoundPostponeEvaluator = checkEvaluator();
        compoundPostponeEvaluator.setTimerTaskScheduled(false);
        return _formatBody();
    }

    synchronized public void close()
    {
        this.closed = true;
        if (getSendOnClose() && eventMap.size() > 0)
        {
            sendBuffer();
        }
    }

    private String _formatBody()
    {
        // Note: this code already owns the monitor for this
        // appender. This frees us from needing to synchronize on 'cb'.

        StringBuffer sbuf = new StringBuffer();
        String t = layout.getHeader();
        if (t != null)
            sbuf.append(t);

        List<String> values = null;;
        synchronized (eventMap)
        {
            values = new ArrayList<String>(eventMap.values());
            eventMap.clear();
        }
        for (String string : values)
        {
            sbuf.append(string);
        }

        t = layout.getFooter();
        if (t != null)
        {
            sbuf.append(t);
        }

        return sbuf.toString();
    }

    public long getPostpone()
    {
        return postpone;
    }

    public void setPostpone(long postpone)
    {
        this.postpone = postpone;
    }

    public static class CompoundPostponeEvaluator implements TriggeringEventEvaluator, OptionHandler
    {
        /**
             Is this <code>event</code> the e-mail triggering event?

             <p>This method returns <code>true</code>, if the event level
             has ERROR level or higher. Otherwise it returns
             <code>false</code>. */
        private final TriggeringEventEvaluator[] evaluators;
        private final Timer                      timer = new Timer("SMTPAppender postpone sending timer", true);
        private Copy_3_of_PostponeSMTPAppender             postponeSMTPAppender;
        private TimerTask                        currTimerTask;
        //    private boolean                          timerTaskStarted;
        private boolean                          timerTaskScheduled;

        public CompoundPostponeEvaluator(TriggeringEventEvaluator... evaluators)
        {
            this.evaluators = evaluators;
        }

        void setSMTPAppender(Copy_3_of_PostponeSMTPAppender postponeSMTPAppender)
        {
            this.postponeSMTPAppender = postponeSMTPAppender;
        }

        public void activateOptions()
        {
            for (int i = 0; i < evaluators.length; i++)
            {
                TriggeringEventEvaluator evaluator = evaluators[i];
                if (evaluator instanceof OptionHandler)
                {
                    OptionHandler handler = (OptionHandler) evaluator;
                    handler.activateOptions();
                }
            }
        }

        public boolean isTriggeringEvent(LoggingEvent event)
        {
            boolean isTriggeringEvent = true;
            for (int i = 0; i < evaluators.length && isTriggeringEvent; i++)
            {
                TriggeringEventEvaluator evaluator = evaluators[i];
                isTriggeringEvent &= evaluator.isTriggeringEvent(event);
            }

            if (isTriggeringEvent)
            {
                resetTimer();
            }
            return false;
        }

        private void resetTimer()
        {
            if (!isTimerTaskScheduled())
            {
                synchronized (postponeSMTPAppender)
                {
                    if (!isTimerTaskScheduled())
                    {
                        setTimerTaskScheduled(true);
                        currTimerTask = new TimerTask()
                        {
                            @Override
                            public void run()
                            {
                                postponeSMTPAppender.sendBuffer();
                            }
                        };
                        timer.schedule(currTimerTask, postponeSMTPAppender.getPostpone());
                    }
                }
            }
        }

        public boolean isTimerTaskScheduled()
        {
            return timerTaskScheduled;
        }

        public void setTimerTaskScheduled(boolean timerTaskScheduled)
        {
            this.timerTaskScheduled = timerTaskScheduled;
        }
    }
    
    @Override
    public void append(LoggingEvent event)
    {
        super.append(event);
        registerLogEvent(event);
    }

    protected Map<String, String> eventMap = new HashMap<String, String>();

    protected void registerLogEvent(LoggingEvent event)
    {
        String renderedMessage = event.getRenderedMessage();
        String throwableToString = throwableToString(event);

        String key = renderedMessage + throwableToString;
        String value = layout.format(event) + throwableToString;
        synchronized (eventMap)
        {
            eventMap.put(key, value);
        }
    }

    protected String throwableToString(LoggingEvent event)
    {
        StringBuilder sbuf = new StringBuilder();
        if (layout.ignoresThrowable())
        {
            String[] s = event.getThrowableStrRep();
            if (s != null)
            {
                for (int j = 0; j < s.length; j++)
                {
                    sbuf.append(s[j]);
                    sbuf.append(Layout.LINE_SEP);
                }
            }
        }
        return sbuf.toString();
    }
}