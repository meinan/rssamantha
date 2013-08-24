/*
 *  RSSamantha is a rss/atom feedaggregator.
 *  Copyright (C) 2011-2013  David Schröer <tengcomplexATgmail.com>
 *
 *
 *  This file is part of RSSamantha.
 *
 *  RSSamantha is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  RSSamantha is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with RSSamantha.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.drinschinz.rssamantha;

/**
 *
 * @author teng
 */
import java.io.*;
import java.net.*;
import java.util.*;

/** This class downloads a file from a URL. */
class Download extends Observable implements Runnable
{
    /** Max size of download buffer. */
    private static final int MAX_BUFFER_SIZE = 1024;
    /** These are the status names. */
    public static final String STATUSES[] =
    {
        "Downloading",
        "Paused", "Complete", "Cancelled", "Error"
    };
    // These are the status codes.
    public static final int DOWNLOADING = 0;
    public static final int PAUSED = 1;
    public static final int COMPLETE = 2;
    public static final int CANCELLED = 3;
    public static final int ERROR = 4;
    private final URL url; // download URL
    private final String targetfolder;
    private int size; // size of download in bytes
    private int downloaded; // number of bytes downloaded
    private int status; // current status of download
    private Item item;
    
    public Download(final Item i) throws Exception
    {
        this.url = new URL(i.getElements().getElementValue("contenturl"));
        this.targetfolder = i.getElements().getElementValue("contentfolder");
        size = -1;
        downloaded = 0;
        status = DOWNLOADING;
        this.item = i;
        // Begin the download.
        download();
    }

    public Item getItem()
    {
        return this.item;
    }

    /** Get this download's URL. */
    public String getUrl()
    {
        return url.toString();
    }

    /** Get this download's size. */
    public int getSize()
    {
        return size;
    }

    /** Get this download's progress. */
    public float getProgress()
    {
        return ((float) downloaded / size) * 100;
    }

    /** Get this download's status. */
    public int getStatus()
    {
        return status;
    }

    /** Pause this download. */
    public void pause()
    {
        status = PAUSED;
        stateChanged();
    }

    /** Resume this download. */
    public void resume()
    {
        status = DOWNLOADING;
        stateChanged();
        download();
    }

    /** Cancel this download. */
    public void cancel()
    {
        status = CANCELLED;
        stateChanged();
    }

    /** Mark this download as having an error. */
    private void error()
    {
        status = ERROR;
        stateChanged();
    }

    /** Start or resume downloading. */
    private void download()
    {
        final Thread thread = new Thread(this);
        thread.start();
    }

    /** Get file name portion of URL. */
    public static String getFileName(final String url)
    { 
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /** Download file. */
    public void run()
    {
        RandomAccessFile file = null;
        InputStream stream = null;

        try
        {
            // Open connection to URL.
            final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            // Specify what portion of file to download.
            connection.setRequestProperty("Range", "bytes=" + downloaded + "-");
            // Connect to server.
            connection.connect();
            // Make sure response code is in the 200 range.
            if(connection.getResponseCode() / 100 != 2)
            {
                error();
            }
            // Check for valid content length.
            final int contentLength = connection.getContentLength();
            if (contentLength < 1)
            {
                error();
            }
            /* Set the size for this download if it
            hasn't been already set. */
            if(size == -1)
            {
                size = contentLength;
                stateChanged();
            }
            // Open file and seek to the end of it.
            file = new RandomAccessFile(targetfolder+getFileName(url.getFile()), "rw");
            file.seek(downloaded);
            stream = connection.getInputStream();
            while(status == DOWNLOADING)
            {
                /* Size buffer according to how much of the
                file is left to download. */
                byte buffer[];
                if(size - downloaded > MAX_BUFFER_SIZE)
                {
                    buffer = new byte[MAX_BUFFER_SIZE];
                }
                else
                {
                    buffer = new byte[size - downloaded];
                }
                // Read from server into buffer.
                int read = stream.read(buffer);
                if (read == -1)
                {
                    break;
                }

                // Write buffer to file.
                file.write(buffer, 0, read);
                downloaded += read;
                stateChanged();
            }

            /* Change status to complete if this point was
            reached because downloading has finished. */
            if (status == DOWNLOADING)
            {
                status = COMPLETE;
                stateChanged();
            }
        }
        catch (Exception e)
        {
            error();
        }
        finally
        {
            // Close file.
            if(file != null)
            {
                try
                {
                    file.close();
                }
                catch (Exception e)
                {
                }
            }
            // Close connection to server.
            if(stream != null)
            {
                try
                {
                    stream.close();
                }
                catch (Exception e)
                {
                }
            }
        }
    }

    /** Notify observers that this download's status has changed. */
    private void stateChanged()
    {
        setChanged();
        notifyObservers();
    }
}
