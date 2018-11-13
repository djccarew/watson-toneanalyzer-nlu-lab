import sys
import json
import glob
from watson_developer_cloud import ToneAnalyzerV3
from watson_developer_cloud import NaturalLanguageUnderstandingV1
from watson_developer_cloud.natural_language_understanding_v1 import Features, KeywordsOptions, SemanticRolesOptions
from operator import itemgetter
import settings

# This Watson Python SDK example uses the Tone Analyzer service to extract the most positive sentences
# from earnings call transcripts and then runs those remarks through the Natural Language Understanding
# service to extract the most relevant keywords and Semantic Roles  from those  sentences extracted by
# Tone Analyzer. The companion file settings.py is where the service credentials and location
# of the call transcript files are provided to this application

def main():
   # Check for service credentials and transcript files location
   if not hasattr(settings, 'TONE_ANALYZER_APIKEY')  or not hasattr(settings, 'NLU_APIKEY') or not hasattr(settings, 'TEST_DATA_DIR'):
       print("Error: Service credentials and/or test data dir  missing. Terminating ...")
       sys.exit(1)
   else:
       tone_analyzer_apikey =  settings.TONE_ANALYZER_APIKEY
       nlu_apikey = settings.NLU_APIKEY
       test_data_dir = settings.TEST_DATA_DIR

        # Create service clients
       tone_analyzer = ToneAnalyzerV3(iam_apikey= tone_analyzer_apikey, version='2017-09-21')

       natural_language_understanding = NaturalLanguageUnderstandingV1(version='2018-03-16', iam_apikey=nlu_apikey)

       # Loop through all call transcript files
       test_files = glob.glob(test_data_dir + '/**/*.txt', recursive=True)
       print('Analyzing  %d earnings call transcripts ...' % (len(test_files)))
       for filename in  test_files:
           print("Analyzing transcript file name " + filename)

           with open(filename, 'r') as transcript:

              tone = tone_analyzer.tone(tone_input=transcript.read(), content_type="text/plain")

              # Get joy and sort by descending score
              sentences_with_joy = []
              for each_sentence in tone['sentences_tone']:
                 for each_tone in each_sentence['tones']:
                    if each_tone['tone_id'] == 'joy':
                       sentences_with_joy.append({'sentence_id': each_sentence['sentence_id'], 'text': each_sentence['text'], 'score': each_tone['score']})
                       break

              sentences_with_joy = sorted(sentences_with_joy, key=itemgetter('score'), reverse=True)
              # Only top 5 are being selected
              if len(sentences_with_joy) > 5:
                   sentences_with_joy = sentences_with_joy[:5]


              index = 1
              print('\nMost positive statements from earnings call:\n')

              # Go through top positive sentences and use NLU to get keywords and
              # Semantic Roles
              for sentence in sentences_with_joy:
                 print(str(index) + ') ' + sentence['text'])
                 nlu_analysis = natural_language_understanding.analyze(text = sentence['text'], features=Features(keywords=KeywordsOptions(), semantic_roles=SemanticRolesOptions(keywords=True)))
                 first_keyword = True
                 for each_item in nlu_analysis['keywords']:
                    if each_item['relevance'] > 0.5:
                        if first_keyword:
                            print('')
                            print('NLU Analysis:')
                            print('keywords: ' + each_item['text'], end='')
                            first_keyword = False
                        else:
                            print(', ' + each_item['text'], end='')
                 print('')
                 first_semantic_role = True
                 for each_item in nlu_analysis['semantic_roles']:
                    if first_semantic_role:
                       print('semantic_roles:')
                       first_semantic_role = False
                    subject_dict = each_item.get('subject')
                    if subject_dict is None:
                       print('subject: N/A ', end='')
                    else:
                       print('subject: ' + subject_dict['text'], end=' ')

                    action_dict = each_item.get('action')
                    if action_dict is None:
                       print('action: N/A ', end='')
                    else:
                       print('action: ' + action_dict['text'], end=' ')

                    object_dict = each_item.get('object')
                    if object_dict is None:
                       print('object: N/A', end='')
                    else:
                       print('object: ' + object_dict['text'], end='')
                    print()

                 index = index + 1
                 print('\n')

       print('Processing complete. Exiting ...')

if __name__ == "__main__":
   main()
