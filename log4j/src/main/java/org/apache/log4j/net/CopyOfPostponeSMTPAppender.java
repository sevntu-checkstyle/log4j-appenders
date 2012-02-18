/* Project: log4j
 * File: PostponeSMTPAppender.java
 * Created on Dec 3, 2011
 *  
 * Author: Sergiy Goncharenko
 * Copyright 2011, Edifecs Inc.
 */
package org.apache.log4j.net;

import java.util.Timer;
import java.util.TimerTask;

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
public class CopyOfPostponeSMTPAppender extends SMTPAppender
{
    /*
     * postpone - a time in milliseconds to postpone sending
     * if postpone is equals -1 the message will send immediately
     */
    private long postpone = -1;

    public CopyOfPostponeSMTPAppender()
    {
        this(new CompoundPostponeEvaluator(new DefaultEvaluator()));
    }

    public CopyOfPostponeSMTPAppender(TriggeringEventEvaluator evaluator)
    {
        super(new CompoundPostponeEvaluator(new DefaultEvaluator(), evaluator));
    }

    public void setEvaluatorClass(String value)
    {
        if (!CompoundPostponeEvaluator.class.getClass().getName().equals(value))
        {
            setEvaluator(new CompoundPostponeEvaluator((TriggeringEventEvaluator) OptionConverter.instantiateByClassName(value, TriggeringEventEvaluator.class, evaluator)));
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
            checkAndCorrectEvaluator();
        }
        return checkEntryConditions;
    }

    protected CompoundPostponeEvaluator checkAndCorrectEvaluator()
    {
        TriggeringEventEvaluator evaluator2 = getEvaluator();
        if (!(evaluator2 instanceof CompoundPostponeEvaluator))
        {
            setEvaluator(new CompoundPostponeEvaluator(getEvaluator()));
        }
        return (CompoundPostponeEvaluator) getEvaluator();
    }

    //    public final void setEvaluator(final TriggeringEventEvaluator trigger)
    //    {
    //        if (trigger == null)
    //        {
    //            throw new NullPointerException("trigger");
    //        }
    //        this.evaluator = trigger;
    //    }

    @Override
    protected synchronized String formatBody()
    {
        CompoundPostponeEvaluator compoundPostponeEvaluator = checkAndCorrectEvaluator();
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
}

class CompoundPostponeEvaluator implements TriggeringEventEvaluator, OptionHandler
{
    /**
         Is this <code>event</code> the e-mail triggering event?

         <p>This method returns <code>true</code>, if the event level
         has ERROR level or higher. Otherwise it returns
         <code>false</code>. */
    private final TriggeringEventEvaluator[] evaluators;
    private final Timer                      timer = new Timer("SMTPAppender postpone sending timer", true);
    private CopyOfPostponeSMTPAppender             postponeSMTPAppender;
    private TimerTask                        currTimerTask;
    //    private boolean                          timerTaskStarted;
    private boolean                          timerTaskScheduled;

    CompoundPostponeEvaluator(TriggeringEventEvaluator... evaluators)
    {
        this.evaluators = evaluators;
    }

    void setSMTPAppender(CopyOfPostponeSMTPAppender postponeSMTPAppender)
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
