package PoliTweetsCL.Collector;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import PoliTweetsCL.Core.BD.MongoDBController;
import PoliTweetsCL.Core.BD.MySQLController;
import PoliTweetsCL.Core.Model.Tweet;

import twitter4j.FilterQuery;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;



public class TwitterStreaming {

	private final TwitterStream twitterStream;
	private Set<String> keywords;
	private SentimentAnalyzer sentimentAnalyzer;
	private Properties prop = null;


	private TwitterStreaming() {
		loadProperties();
		this.twitterStream = new TwitterStreamFactory().getInstance();
		this.keywords = new HashSet<>();
		loadKeywords();
		sentimentAnalyzer = new SentimentAnalyzer();
	}

	private void loadProperties(){
		try{
			prop = new Properties();
			FileInputStream file;
			File jarPath=new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
			String propertiesPath=jarPath.getParent();
			prop.load(new FileInputStream(propertiesPath+ "/app.properties"));
		}catch (Exception e){
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			try(InputStream resourceStream = loader.getResourceAsStream("appDefault.properties")) {
				prop.load(resourceStream);
			}catch (Exception ioe){
				ioe.printStackTrace();
			}
		}
	}

	private void loadKeywords() {
		MySQLController sqlDB = new MySQLController(prop);

		keywords = sqlDB.getKeywords();
	}

	private void init() {
		StatusListener listener = new StatusListener() {
			private MongoDBController db = new MongoDBController(prop);

			public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
				System.out.println("Got a status deletion notice id:" + statusDeletionNotice.getStatusId());
			}

			public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
				System.out.println("Got track limitation notice:" + numberOfLimitedStatuses);
			}

			public void onScrubGeo(long userId, long upToStatusId) {
				System.out.println("Got scrub_geo event userId:" + userId + " upToStatusId:" + upToStatusId);
			}

			public void onException(Exception ex) {
				ex.printStackTrace();
			}

			@Override
			public void onStallWarning(StallWarning arg0) {

			}

			@Override
			public void onStatus(Status status) {
				if(Objects.equals(status.getLang(), "es")){
					// generar tweet
					Tweet tweet = new Tweet(status);

					// obtener sentimiento
					tweet.setSentimiento(sentimentAnalyzer.findSentiment(tweet.getText()));

					// guardar en mongodb
					db.saveTweet(tweet);
				}
			}
		};

		FilterQuery fq = new FilterQuery();

		fq.track(keywords.toArray(new String[0]));

		this.twitterStream.addListener(listener);
		this.twitterStream.filter(fq);

	}
	
	public static void main(String[] args) {
		new TwitterStreaming().init();
	}

}
