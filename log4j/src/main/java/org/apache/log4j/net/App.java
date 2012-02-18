package org.apache.log4j.net;

import java.io.IOException;

import org.apache.log4j.Logger;

/**
 * Hello world!
 *
 */
public class App
{
    Logger logger = Logger.getLogger(this.getClass());

    public void logSomeInfo() throws InterruptedException
    {
        int i = 0;
        while(true)
        {
            logger.error("", new Exception("it is my new exception"));
            logger.error("", new IOException("it is my new io exception "/* + (i++)*/));
            Thread.sleep(100);
        }
    }

    public static void main(String[] args) throws InterruptedException
    {
        App app = new App();
        app.logSomeInfo();
    }
}
