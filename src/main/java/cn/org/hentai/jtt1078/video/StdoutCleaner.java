package cn.org.hentai.jtt1078.video;

import cn.org.hentai.jtt1078.util.Configs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * Created by matrixy on 2019/6/29.
 */
public class StdoutCleaner extends Thread
{
    static Logger logger = LoggerFactory.getLogger(StdoutCleaner.class);

    Object lock = null;
    boolean debugMode = false;
    HashMap<Long, Process> processes;
    LinkedList<Long> readyToClose;

    private StdoutCleaner()
    {
        lock = new Object();
        processes = new HashMap<>(128);
        readyToClose = new LinkedList<>();

        setName("stdout-cleaner");
        debugMode = "true".equalsIgnoreCase(Configs.get("ffmpeg.debug"));
    }

    public void watch(Long channel, Process process)
    {
        synchronized (lock)
        {
            processes.put(channel, process);
        }

        logger.debug("watch: {}", channel);
    }

    public void unwatch(Long channel)
    {
        synchronized (lock)
        {
            readyToClose.add(channel);
        }

        logger.debug("unwatch: {}", channel);
    }

    public void run()
    {
        byte[] block = new byte[512];
        while (!this.isInterrupted())
        {
            try
            {
                List<Long> channels = null;
                synchronized (lock)
                {
                    channels = Arrays.asList(processes.keySet().toArray(new Long[0]));
                }
                for (int i = 0; i < channels.size(); i++)
                {
                    Long channel = channels.get(i);
                    Process process = processes.get(channel);
                    if (process.isAlive() == false)
                    {
                        synchronized (lock)
                        {
                            readyToClose.add(channel);
                        }
                        continue;
                    }

                    // 清理一下输出流
                    InputStream stdout = process.getInputStream();
                    InputStream stderr = process.getErrorStream();

                    int buffLength = 0;
                    try { buffLength = stdout.available(); }
                    catch (IOException e)
                    {
                        synchronized (lock)
                        {
                            readyToClose.add(channel);
                        }
                        break;
                    }
                    if (buffLength > 0)
                    {
                        int x = stdout.read(block, 0, Math.min(buffLength, block.length));
                        if (debugMode) System.out.print(new String(block, 0, x));
                    }

                    try { buffLength = stderr.available(); }
                    catch (IOException e)
                    {
                        synchronized (lock)
                        {
                            readyToClose.add(channel);
                        }
                        break;
                    }
                    if (buffLength > 0)
                    {
                        int x = stderr.read(block, 0, Math.min(buffLength, block.length));
                        if (debugMode) System.out.print(new String(block, 0, x));
                    }
                }

                synchronized (lock)
                {
                    while (readyToClose.size() > 0)
                    {
                        Long channel = readyToClose.removeFirst();
                        if (processes.containsKey(channel))
                        {
                            processes.remove(channel);
                        }
                    }
                }

                Thread.sleep(2);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    static StdoutCleaner instance;

    public static synchronized void init()
    {
        instance = new StdoutCleaner();
        instance.start();
    }

    public static synchronized StdoutCleaner getInstance()
    {
        return instance;
    }
}
