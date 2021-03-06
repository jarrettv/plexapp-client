/**
 * The MIT License (MIT)
 * Copyright (c) 2012 David Carver
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.github.kingargyle.plexappclient.core.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.github.kingargyle.plexapp.model.impl.Director;
import com.github.kingargyle.plexapp.model.impl.Genre;
import com.github.kingargyle.plexapp.model.impl.Media;
import com.github.kingargyle.plexapp.model.impl.MediaContainer;
import com.github.kingargyle.plexapp.model.impl.Part;
import com.github.kingargyle.plexapp.model.impl.Video;
import com.github.kingargyle.plexapp.model.impl.Writer;
import com.github.kingargyle.plexappclient.SerenityApplication;
import com.github.kingargyle.plexappclient.core.model.VideoContentInfo;
import com.github.kingargyle.plexappclient.core.model.impl.EpisodePosterInfo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

/**
 * @author dcarver
 * 
 */
public class EpisodeRetrievalIntentService extends AbstractPlexRESTIntentService {

	protected List<VideoContentInfo> posterList = null;
	protected String key;
	

	public EpisodeRetrievalIntentService() {
		super("EpisodeRetrievalIntentService");
		posterList = new ArrayList<VideoContentInfo>();

	}

	@Override
	public void sendMessageResults(Intent intent) {
		Bundle extras = intent.getExtras();
		if (extras != null) {
			Messenger messenger = (Messenger) extras.get("MESSENGER");
			Message msg = Message.obtain();
			msg.obj = posterList;
			try {
				messenger.send(msg);
			} catch (RemoteException ex) {
				Log.e(getClass().getName(), "Unable to send message", ex);
			}
		}
		

	}

	@Override
	protected void onHandleIntent(Intent intent) {
		key = intent.getExtras().getString("key", "");
		createPosters();
		sendMessageResults(intent);
	}

	protected void createPosters() {
		MediaContainer mc = null;
		try {
			factory = SerenityApplication.getPlexFactory();
			mc = retrieveVideos();
		} catch (IOException ex) {
			Log.e(getClass().getName(),
					"Unable to talk to server: ", ex);
		} catch (Exception e) {
			Log.e(getClass().getName(), "Oops.", e);
		}

		if (mc != null && mc.getSize() > 0) {
			createVideoContent(mc);
		}

	}


	/**
	 * 
	 * @return A media container with episodes or videos
	 * 
	 * @throws Exception
	 */
	protected MediaContainer retrieveVideos() throws Exception {
		MediaContainer mc;
		mc = factory.retrieveEpisodes(key);
		return mc;
	}
	
	/**
	 * @param mc
	 * @param baseUrl
	 */
	protected void createVideoContent(MediaContainer mc) {
		String baseUrl = factory.baseURL();
		String parentPosterURL = null;
		if (mc.getParentPosterURL() != null) {
			parentPosterURL = baseUrl + mc.getParentPosterURL().substring(1);
		}
		List<Video> videos = mc.getVideos();
		for (Video episode : videos) {
			VideoContentInfo  epi = new EpisodePosterInfo();
			epi.setParentPosterURL(parentPosterURL);
			epi.setId(episode.getRatingKey());
			epi.setPlotSummary(episode.getSummary());
			epi.setViewCount(episode.getViewCount());
			epi.setResumeOffset(Long.valueOf(episode.getViewOffset()).intValue());			
			
			if (episode.getParentThumbNailImageKey() != null) {
				epi.setParentPosterURL(baseUrl + episode.getParentThumbNailImageKey().substring(1));
			}
			
			String burl = factory.baseURL() + ":/resources/show-fanart.jpg"; 
			if (mc.getArt() != null) {
				burl = baseUrl + mc.getArt().replaceFirst("/", "");
			}
			epi.setBackgroundURL(burl);
			
			String turl = "";
			if (episode.getThumbNailImageKey() != null) {
				turl = baseUrl + episode.getThumbNailImageKey().replaceFirst("/", "");
			}
			
			epi.setPosterURL(turl);
			epi.setTitle(episode.getTitle());
			
			epi.setContentRating(episode.getContentRating());
			
			List<Media> mediacont = episode.getMedias();
			if (mediacont != null && !mediacont.isEmpty()) {
				// We grab the first media container until we know more about why there can be multiples.
				Media media = mediacont.get(0);
				List<Part> parts = media.getVideoPart();
				Part part = parts.get(0);
				epi.setAudioCodec(media.getAudioCodec());
				epi.setVideoCodec(media.getVideoCodec());
				epi.setVideoResolution(media.getVideoResolution());
				epi.setAspectRatio(media.getAspectRatio());
				epi.setAudioChannels(media.getAudioChannels());
				
				String directPlayUrl = factory.baseURL() + part.getKey().replaceFirst("/", "");
				epi.setDirectPlayUrl(directPlayUrl);
				
			}
			
			String episodeDetails = createVideoDetails(episode, epi);
			epi.setCastInfo(episodeDetails);				
		
			posterList.add(epi);
		}
	}
	

	/**
	 * @param video
	 * @param videoContentInfo
	 * @return
	 */
	protected String createVideoDetails(Video video,
			VideoContentInfo videoContentInfo) {
		String videoDetails = "";

		if (video.getYear() != null) {
			videoContentInfo.setYear(video.getYear());
			videoDetails = "Year: " + video.getYear();
			videoDetails = videoDetails + "\r\n";
		}

		if (video.getGenres() != null && video.getGenres().size() > 0) {
			ArrayList<String> g = new ArrayList<String>();
			for (Genre genre : video.getGenres()) {
				g.add(genre.getTag());
				videoDetails = videoDetails + genre.getTag() + "/";
			}
			videoContentInfo.setGenres(g);
			videoDetails = videoDetails.substring(0,
					videoDetails.lastIndexOf("/"));
			videoDetails = videoDetails + "\r\n";
		}

		if (video.getWriters() != null && video.getWriters().size() > 0) {
			videoDetails = videoDetails + "Writer(s): ";
			ArrayList<String> w = new ArrayList<String>();
			for (Writer writer : video.getWriters()) {
				w.add(writer.getTag());
				videoDetails = videoDetails + writer.getTag() + ", ";
			}
			videoContentInfo.setWriters(w);
			videoDetails = videoDetails.substring(0,
					videoDetails.lastIndexOf(","));
			videoDetails = videoDetails + "\r\n";
		}

		if (video.getDirectors() != null && video.getDirectors().size() > 0) {
			videoDetails = videoDetails + "Director(s): ";
			ArrayList<String> d = new ArrayList<String>();
			for (Director director : video.getDirectors()) {
				d.add(director.getTag());
				videoDetails = videoDetails + director.getTag() + ", ";
			}
			videoContentInfo.setDirectors(d);
			videoDetails = videoDetails.substring(0,
					videoDetails.lastIndexOf(","));
			videoDetails = videoDetails + "\r\n";
		}
		return videoDetails;
	}

}
