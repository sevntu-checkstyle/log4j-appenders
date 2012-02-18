/* Project: log4j
 * File: PostponeSMTPAppender.java
 * Created on Dec 3, 2011
 *  
 * Author: Sergiy Goncharenko
 * Copyright 2011, Edifecs Inc.
 */
package org.apache.log4j.net;

import java.util.HashMap;
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
public class Copy_2_of_PostponeSMTPAppender extends SMTPAppender
{
    /*
     * postpone - a time in milliseconds to postpone sending
     * if postpone is equals -1 the message will send immediately
     */
    private long postpone = -1;

    public Copy_2_of_PostponeSMTPAppender()
    {
        this(new CompoundPostponeEvaluator(new DefaultEvaluator()));
    }

    public Copy_2_of_PostponeSMTPAppender(CompoundPostponeEvaluator compoundPostponeEvaluator)
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
        return super.formatBody();
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
        private Copy_2_of_PostponeSMTPAppender             postponeSMTPAppender;
        private TimerTask                        currTimerTask;
        //    private boolean                          timerTaskStarted;
        private boolean                          timerTaskScheduled;

        public CompoundPostponeEvaluator(TriggeringEventEvaluator... evaluators)
        {
            this.evaluators = evaluators;
        }

        void setSMTPAppender(Copy_2_of_PostponeSMTPAppender postponeSMTPAppender)
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
}