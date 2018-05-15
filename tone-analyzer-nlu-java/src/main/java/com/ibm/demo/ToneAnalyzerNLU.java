package com.ibm.demo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;

import com.ibm.watson.developer_cloud.natural_language_understanding.v1.NaturalLanguageUnderstanding;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalyzeOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalysisResults;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.Features;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.KeywordsOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.KeywordsResult;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.SemanticRolesOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.SemanticRolesResult;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.ToneAnalyzer;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.SentenceAnalysis;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.ToneAnalysis;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.ToneOptions;
import com.ibm.watson.developer_cloud.tone_analyzer.v3.model.ToneScore;
import com.ibm.watson.developer_cloud.util.HttpLogging;

import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

// This Watson Java SDK example uses the Tone Analyzer service to extract the most positive sentences 
// from earnings call transcripts and then runs those remarks through the Natural Language Understanding 
// service to extract the most relevant keywords and Semantic Roles  from those  sentences extracted by 
// Tone Analyzer. The companion file settings.properties is where the service credentials and location
// of the call transcript files are provided to this application

public class ToneAnalyzerNLU {

	@SuppressWarnings("deprecation")
	public static void main(String[] args) throws IOException {

		// Getting required properties
		final Properties properties = new Properties();
		try (final InputStream stream = ToneAnalyzerNLU.class.getResourceAsStream("/settings.properties")) {
			properties.load(stream);
		}

		// Exit if required properties not present
		if (properties.getProperty("TONE_ANALYZER_USER") == null
				|| properties.getProperty("TONE_ANALYZER_PASSWORD") == null
				|| properties.getProperty("NLU_USER") == null || properties.getProperty("NLU_PASSWORD") == null
				|| properties.getProperty("TEST_DATA_DIR") == null) {
			System.err.println("Error: Service credentials and/or test data dir  missing. Terminating ...");
			System.exit(1);
		}

		// Create service clients
		ToneAnalyzer toneAnalyzer = new ToneAnalyzer("2017-09-26");
		toneAnalyzer.setUsernameAndPassword(properties.getProperty("TONE_ANALYZER_USER"),
				properties.getProperty("TONE_ANALYZER_PASSWORD"));

		NaturalLanguageUnderstanding nlu = new NaturalLanguageUnderstanding("2018-03-16");
		nlu.setUsernameAndPassword(properties.getProperty("NLU_USER"), properties.getProperty("NLU_PASSWORD"));
		KeywordsOptions keywordsOptions = new KeywordsOptions.Builder().limit(5).build();
		SemanticRolesOptions semanticRolesOptions = new SemanticRolesOptions.Builder().build();
		Features nluFeatures = new Features.Builder().semanticRoles(semanticRolesOptions).keywords(keywordsOptions)
				.build();

		// Turn off HTTP logging
		// Default is BASIC which logs all HTTP requests to the Watson services
		// Commenting out these 2 lines will revert to the default behavior
		HttpLoggingInterceptor httpLogger = HttpLogging.getLoggingInterceptor();
		httpLogger.setLevel(Level.NONE);

		// Get all txt files in test data folder
		File dir = new File(properties.getProperty("TEST_DATA_DIR"));
		String[] extensions = new String[] { "txt" };
		
		if (!dir.isDirectory()) {
			dir = new File(properties.getProperty("TEST_DATA_DIR").replaceAll("\\.\\.", ""));
		}
		
		List<File> files = (List<File>) FileUtils.listFiles(dir, extensions, true);

		System.out.println("Analyzing " + files.size() + " earnings call transcripts");

		// Analyze each file
		for (File file : files) {
			System.out.println("\nAnalyzing transcript filename " + file.getName() + "\n");
			String transcript = FileUtils.readFileToString(file);

			// Get sentences that indicate joy
			ToneOptions toneOptions = new ToneOptions.Builder().sentences(true).text(transcript).build();
			ToneAnalysis tone = toneAnalyzer.tone(toneOptions).execute();
			ArrayList<JoyScore> sentencesWithJoy = new ArrayList<JoyScore>();
			List<SentenceAnalysis> sentenceAnalysis = tone.getSentencesTone();
			for (SentenceAnalysis eachSentence : sentenceAnalysis) {
				List<ToneScore> toneScores = eachSentence.getTones();
				for (ToneScore eachScore : toneScores) {
					// If joy is detected save the sentence and the score
					if (eachScore.getToneId().equals("joy")) {
						JoyScore joyScore = new JoyScore();
						joyScore.setScore(eachScore.getScore());
						joyScore.setText(eachSentence.getText());
						sentencesWithJoy.add(joyScore);
					}
				}

			}

			// Order saved sentences by descending score
			Collections.sort(sentencesWithJoy);
			// Keep only top 5
			if (sentencesWithJoy.size() > 5) {
				sentencesWithJoy.subList(5, sentencesWithJoy.size()).clear();
			}

			System.out.println("\nMost positive statements from earnings call:\n");
			// Print 3 sentences with the highest joy score
			for (int i = 0; i < sentencesWithJoy.size(); i++) {
				System.out.println(String.valueOf(i + 1) + ") " + sentencesWithJoy.get(i).getText() + "\n");

				// For each sentence call NLU to get keywords and semantic roles
				AnalyzeOptions analyzeOptions = new AnalyzeOptions.Builder().text(sentencesWithJoy.get(i).getText())
						.features(nluFeatures).build();
				AnalysisResults analysisResults = nlu.analyze(analyzeOptions).execute();
				List<KeywordsResult> keywords = analysisResults.getKeywords();

				// Print all keywords with relevance> 0.5
				boolean firstKeyword = true;
				for (KeywordsResult eachKeyword : keywords) {
					if (eachKeyword.getRelevance() > 0.5) {
						if (firstKeyword) {
							System.out.print("\nNLU Analysis:\nkeywords: ");
							System.out.print(eachKeyword.getText());
							firstKeyword = false;
						} else {
							System.out.print("," + eachKeyword.getText());
						}
					}
				}
				System.out.println("");

				// Process semantic roles
				// For each role get subject, action and object
				boolean firstSemanticRole = true;
				List<SemanticRolesResult> semanticRoles = analysisResults.getSemanticRoles();
				for (SemanticRolesResult eachSemanticRole : semanticRoles) {
					if (firstSemanticRole) {
						System.out.println("semantic roles: ");
						firstSemanticRole = false;
					}
					if (eachSemanticRole.getSubject() == null)
						System.out.print("subject: N/A ");
					else
						System.out.print("subject: " + eachSemanticRole.getSubject().getText() + " ");

					if (eachSemanticRole.getAction() == null)
						System.out.print("action: N/A ");
					else
						System.out.print("action: " + eachSemanticRole.getAction().getText() + " ");

					if (eachSemanticRole.getObject() == null)
						System.out.print("object: N/A ");
					else
						System.out.print("object: " + eachSemanticRole.getObject().getText() + " ");
					System.out.println("");
				}
				System.out.println("");
			}

		}

	}

}

// Helper class to save text of sentence and the Tone Analyzer score for joy
// Implements the Comparable interface so a list of these objects can
// be sorted
class JoyScore implements java.lang.Comparable<JoyScore> {

	private double score;
	private String text;

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	// Will cause a list of these object to be sorted sort by score in descending
	// order
	@Override
	public int compareTo(JoyScore other) {
		if (this.score > other.getScore())
			return -1;
		else if (this.score < other.getScore())
			return 1;
		else
			return 0;
	}
}
