package proxy;

import java.util.concurrent.BlockingQueue;

import http.HttpClient;
import http.HttpClient10;
import media.MovieManifest;
import media.MovieManifest.Manifest;
import media.MovieManifest.Segment;
import media.MovieManifest.SegmentContent;
import media.MovieManifest.Track;
import proxy.server.ProxyServer;

public class Main {
	static final String MEDIA_SERVER_BASE_URL = "http://localhost:9999";

	public static void main(String[] args) throws Exception {

		ProxyServer.start( (movie, queue) -> new DashPlaybackHandler(movie, queue) );
		
	}
	/**
	 * TODO TODO TODO TODO
	 * 
	 * Class that implements the client-side logic.
	 * 
	 * Feeds the player queue with movie segment data fetched
	 * from the HTTP server.
	 * 
	 * The fetch algorithm should prioritize:
	 * 1) avoid stalling the browser player by allowing the queue to go empty
	 * 2) if network conditions allow, retrieve segments from higher quality tracks
	 */
	static class DashPlaybackHandler implements Runnable  {
		
		final String movie;
		final Manifest manifest;
		final BlockingQueue<SegmentContent> queue;
		int currentSegment;
		final HttpClient http;
		
		DashPlaybackHandler( String movie, BlockingQueue<SegmentContent> queue) {
			this.movie = movie;
			this.queue = queue;
			
			this.http = new HttpClient10();
			
			
			new MovieManifest();
			this.manifest = MovieManifest.parse(http.doGet(movie+"/"+"manifest.txt").toString());
			
			int bandwidth = 1000000;//need to calculate this in before every segment is put in queue
			Track track=null;
			for(Track t :manifest.tracks()) {
				if(track == null )
					track = t;
				if(t.avgBandwidth()<bandwidth && t.avgBandwidth() > track.avgBandwidth())
					track = t;
			}
			this.queue.add(new SegmentContent(track.contentType(), http.doGetRange(movie+"/"+track.filename(), track.segments().get(0).offset(), track.segments().get(currentSegment).offset()+track.segments().get(0).length())));
			currentSegment = 1;
		}
		
		/**
		 * Runs automatically in a dedicated thread...
		 * 
		 * Needs to feed the queue with segment data fast enough to
		 * avoid stalling the browser player
		 * 
		 * Upon reaching the end of stream, the queue should
		 * be fed with a zero-length data segment
		 */
		public void run() {
		
			if(currentSegment<manifest.tracks().size()) {
				int bandwidth = 1000000;//need to calculate this in before every segment is put in queue
				Track track=null;
				for(Track t :manifest.tracks()) {
					if(track == null )
						track = t;
					if(t.avgBandwidth()<bandwidth && t.avgBandwidth() > track.avgBandwidth())
						track = t;
				}
				this.queue.add(new SegmentContent(track.contentType(), http.doGetRange(movie+"/"+track.filename(), track.segments().get(currentSegment).offset(), track.segments().get(currentSegment).offset()+track.segments().get(currentSegment++).length())));

				if(currentSegment==manifest.tracks().size()) 
					this.queue.add(new SegmentContent("", http.doGetRange(movie, 0, 0)));
					
			}
		}
	}
}
