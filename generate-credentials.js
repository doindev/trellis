const fs = require('fs');
const path = require('path');

const outDir = path.join(__dirname, 'src/main/java/io/trellis/credentials/impl');

// Already created files - skip these
const existing = new Set([
  'httpBasicAuth','httpHeaderAuth','httpQueryAuth','oAuth2Api',
  'awsApi','googleOAuth2Api','microsoftOAuth2Api',
  'githubOAuth2Api','githubApi','gitlabOAuth2Api','gitlabApi',
  'jiraApi','notionApi','slackOAuth2Api','slackApi',
  'discordOAuth2Api','telegramApi','sendGridApi','twilioApi',
  'openAiApi','anthropicApi','postgresApi','mysqlApi',
  'hubSpotOAuth2Api','s3Api','dropboxOAuth2Api'
]);

// OAuth2 URL database
const oauthUrls = {
  'acuitySchedulingOAuth2Api': ['https://acuityscheduling.com/oauth2/authorize','https://acuityscheduling.com/oauth2/token'],
  'airtableOAuth2Api': ['https://airtable.com/oauth2/v1/authorize','https://airtable.com/oauth2/v1/token'],
  'asanaOAuth2Api': ['https://app.asana.com/-/oauth_authorize','https://app.asana.com/-/oauth_token'],
  'azureStorageOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'beeminderOAuth2Api': ['https://www.beeminder.com/apps/authorize','https://www.beeminder.com/apps/token'],
  'bitlyOAuth2Api': ['https://bitly.com/oauth/authorize','https://api-ssl.bitly.com/oauth/access_token'],
  'boxOAuth2Api': ['https://account.box.com/api/oauth2/authorize','https://api.box.com/oauth2/token'],
  'calendlyOAuth2Api': ['https://auth.calendly.com/oauth/authorize','https://auth.calendly.com/oauth/token'],
  'ciscoWebexOAuth2Api': ['https://webexapis.com/v1/authorize','https://webexapis.com/v1/access_token'],
  'clickUpOAuth2Api': ['https://app.clickup.com/api','https://api.clickup.com/api/v2/oauth/token'],
  'crowdStrikeOAuth2Api': ['https://api.crowdstrike.com/oauth2/authorize','https://api.crowdstrike.com/oauth2/token'],
  'driftOAuth2Api': ['https://dev.drift.com/authorize','https://driftapi.com/oauth2/token'],
  'dropboxOAuth2Api': ['https://www.dropbox.com/oauth2/authorize','https://api.dropboxapi.com/oauth2/token'],
  'eventbriteOAuth2Api': ['https://www.eventbrite.com/oauth/authorize','https://www.eventbrite.com/oauth/token'],
  'facebookLeadAdsOAuth2Api': ['https://www.facebook.com/v17.0/dialog/oauth','https://graph.facebook.com/v17.0/oauth/access_token'],
  'formstackOAuth2Api': ['https://www.formstack.com/api/v2/oauth2/authorize','https://www.formstack.com/api/v2/oauth2/token'],
  'getResponseOAuth2Api': ['https://app.getresponse.com/oauth2_authorize.html','https://api.getresponse.com/v3/token'],
  'gmailOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'goToWebinarOAuth2Api': ['https://api.getgo.com/oauth/v2/authorize','https://api.getgo.com/oauth/v2/token'],
  'gongOAuth2Api': ['https://app.gong.io/oauth2/authorize','https://app.gong.io/oauth2/generate-customer-token'],
  'gSuiteAdminOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'harvestOAuth2Api': ['https://id.getharvest.com/oauth2/authorize','https://id.getharvest.com/api/v2/oauth2/token'],
  'helpScoutOAuth2Api': ['https://secure.helpscout.net/authentication/authorizeClientApplication','https://api.helpscout.net/v2/oauth2/token'],
  'highLevelOAuth2Api': ['https://marketplace.gohighlevel.com/oauth/chooselocation','https://services.leadconnectorhq.com/oauth/token'],
  'hubspotOAuth2Api': ['https://app.hubspot.com/oauth/authorize','https://api.hubapi.com/oauth/v1/token'],
  'keapOAuth2Api': ['https://accounts.infusionsoft.com/app/oauth/authorize','https://api.infusionsoft.com/token'],
  'lineNotifyOAuth2Api': ['https://notify-bot.line.me/oauth/authorize','https://notify-bot.line.me/oauth/token'],
  'linearOAuth2Api': ['https://linear.app/oauth/authorize','https://api.linear.app/oauth/token'],
  'linkedInOAuth2Api': ['https://www.linkedin.com/oauth/v2/authorization','https://www.linkedin.com/oauth/v2/accessToken'],
  'linkedInCommunityManagementOAuth2Api': ['https://www.linkedin.com/oauth/v2/authorization','https://www.linkedin.com/oauth/v2/accessToken'],
  'mailchimpOAuth2Api': ['https://login.mailchimp.com/oauth2/authorize','https://login.mailchimp.com/oauth2/token'],
  'mauticOAuth2Api': ['',''],
  'mediumOAuth2Api': ['https://medium.com/m/oauth/authorize','https://api.medium.com/v1/tokens'],
  'microsoftAzureMonitorOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'microsoftDynamicsOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'microsoftEntraOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'microsoftExcelOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'microsoftGraphSecurityOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'microsoftOneDriveOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'microsoftOutlookOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'microsoftSharePointOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'microsoftTeamsOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'microsoftToDoOAuth2Api': ['https://login.microsoftonline.com/common/oauth2/v2.0/authorize','https://login.microsoftonline.com/common/oauth2/v2.0/token'],
  'miroOAuth2Api': ['https://miro.com/oauth/authorize','https://api.miro.com/v1/oauth/token'],
  'mondayComOAuth2Api': ['https://auth.monday.com/oauth2/authorize','https://auth.monday.com/oauth2/token'],
  'nextCloudOAuth2Api': ['',''],
  'notionOAuth2Api': ['https://api.notion.com/v1/oauth/authorize','https://api.notion.com/v1/oauth/token'],
  'pagerDutyOAuth2Api': ['https://app.pagerduty.com/oauth/authorize','https://app.pagerduty.com/oauth/token'],
  'philipsHueOAuth2Api': ['https://api.meethue.com/v2/oauth2/authorize','https://api.meethue.com/v2/oauth2/token'],
  'pipedriveOAuth2Api': ['https://oauth.pipedrive.com/oauth/authorize','https://oauth.pipedrive.com/oauth/token'],
  'pushbulletOAuth2Api': ['https://www.pushbullet.com/authorize','https://api.pushbullet.com/oauth2/token'],
  'quickBooksOAuth2Api': ['https://appcenter.intuit.com/connect/oauth2','https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer'],
  'raindropOAuth2Api': ['https://raindrop.io/oauth/authorize','https://raindrop.io/oauth/access_token'],
  'redditOAuth2Api': ['https://www.reddit.com/api/v1/authorize','https://www.reddit.com/api/v1/access_token'],
  'salesforceOAuth2Api': ['https://login.salesforce.com/services/oauth2/authorize','https://login.salesforce.com/services/oauth2/token'],
  'sentryIoOAuth2Api': ['https://sentry.io/oauth/authorize/','https://sentry.io/oauth/token/'],
  'serviceNowOAuth2Api': ['',''],
  'shopifyOAuth2Api': ['',''],
  'spotifyOAuth2Api': ['https://accounts.spotify.com/authorize','https://accounts.spotify.com/api/token'],
  'stravaOAuth2Api': ['https://www.strava.com/oauth/authorize','https://www.strava.com/oauth/token'],
  'surveyMonkeyOAuth2Api': ['https://api.surveymonkey.com/oauth/authorize','https://api.surveymonkey.com/oauth/token'],
  'todoistOAuth2Api': ['https://todoist.com/oauth/authorize','https://todoist.com/oauth/access_token'],
  'twistOAuth2Api': ['https://twist.com/oauth/authorize','https://twist.com/oauth/access_token'],
  'twitterOAuth2Api': ['https://twitter.com/i/oauth2/authorize','https://api.twitter.com/2/oauth2/token'],
  'typeformOAuth2Api': ['https://api.typeform.com/oauth/authorize','https://api.typeform.com/oauth/token'],
  'webflowOAuth2Api': ['https://webflow.com/oauth/authorize','https://api.webflow.com/oauth/access_token'],
  'xeroOAuth2Api': ['https://login.xero.com/identity/connect/authorize','https://identity.xero.com/connect/token'],
  'youTubeOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'zendeskOAuth2Api': ['',''],
  'zohoOAuth2Api': ['https://accounts.zoho.com/oauth/v2/auth','https://accounts.zoho.com/oauth/v2/token'],
  'zoomOAuth2Api': ['https://zoom.us/oauth/authorize','https://zoom.us/oauth/token'],
  // Google service-specific
  'googleAdsOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleAnalyticsOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleBigQueryOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleBooksOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleBusinessProfileOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleCalendarOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleChatOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleCloudNaturalLanguageOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleCloudStorageOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleContactsOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleDocsOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleDriveOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleFirebaseCloudFirestoreOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleFirebaseRealtimeDatabaseOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googlePerspectiveOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleSheetsOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleSheetsTriggerOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleSlidesOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleTasksOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
  'googleTranslateOAuth2Api': ['https://accounts.google.com/o/oauth2/v2/auth','https://oauth2.googleapis.com/token'],
};

// Category assignments
const categories = {
  // Generic Auth
  'httpBearerAuth': 'Generic', 'httpCustomAuth': 'Generic', 'httpDigestAuth': 'Generic',
  'httpMultipleHeadersAuth': 'Generic', 'httpSslAuth': 'Generic',
  'oAuth1Api': 'Generic', 'jwtAuth': 'Generic', 'crypto': 'Generic', 'totpApi': 'Generic',
  // Cloud
  'awsAssumeRole': 'Cloud Services',
  'azureStorageOAuth2Api': 'Cloud Services', 'azureStorageSharedKeyApi': 'Cloud Services',
  // Google Services
  'googleApi': 'Google Services', 'googleAdsOAuth2Api': 'Google Services',
  'googleAnalyticsOAuth2Api': 'Google Services', 'googleBigQueryOAuth2Api': 'Google Services',
  'googleBooksOAuth2Api': 'Google Services', 'googleBusinessProfileOAuth2Api': 'Google Services',
  'googleCalendarOAuth2Api': 'Google Services', 'googleChatOAuth2Api': 'Google Services',
  'googleCloudNaturalLanguageOAuth2Api': 'Google Services',
  'googleCloudStorageOAuth2Api': 'Google Services', 'googleContactsOAuth2Api': 'Google Services',
  'googleDocsOAuth2Api': 'Google Services', 'googleDriveOAuth2Api': 'Google Services',
  'googleFirebaseCloudFirestoreOAuth2Api': 'Google Services',
  'googleFirebaseRealtimeDatabaseOAuth2Api': 'Google Services',
  'googlePerspectiveOAuth2Api': 'Google Services', 'googleSheetsOAuth2Api': 'Google Services',
  'googleSheetsTriggerOAuth2Api': 'Google Services', 'googleSlidesOAuth2Api': 'Google Services',
  'googleTasksOAuth2Api': 'Google Services', 'googleTranslateOAuth2Api': 'Google Services',
  'gSuiteAdminOAuth2Api': 'Google Services', 'gmailOAuth2Api': 'Google Services',
  'youTubeOAuth2Api': 'Google Services',
  // Microsoft Services
  'microsoftAzureCosmosDbSharedKeyApi': 'Microsoft Services',
  'microsoftAzureMonitorOAuth2Api': 'Microsoft Services',
  'microsoftDynamicsOAuth2Api': 'Microsoft Services', 'microsoftEntraOAuth2Api': 'Microsoft Services',
  'microsoftExcelOAuth2Api': 'Microsoft Services', 'microsoftGraphSecurityOAuth2Api': 'Microsoft Services',
  'microsoftOneDriveOAuth2Api': 'Microsoft Services', 'microsoftOutlookOAuth2Api': 'Microsoft Services',
  'microsoftSharePointOAuth2Api': 'Microsoft Services', 'microsoftSql': 'Microsoft Services',
  'microsoftTeamsOAuth2Api': 'Microsoft Services', 'microsoftToDoOAuth2Api': 'Microsoft Services',
  // Developer Tools
  'bitbucketAccessTokenApi': 'Developer Tools', 'bitbucketApi': 'Developer Tools',
  'circleCiApi': 'Developer Tools', 'jenkinsApi': 'Developer Tools',
  'linearApi': 'Developer Tools', 'linearOAuth2Api': 'Developer Tools',
  'npmApi': 'Developer Tools', 'travisCiApi': 'Developer Tools',
  'gitPassword': 'Developer Tools', 'rundeckApi': 'Developer Tools',
  'netlifyApi': 'Developer Tools',
  'jiraSoftwareCloudApi': 'Developer Tools', 'jiraSoftwareServerApi': 'Developer Tools',
  'jiraSoftwareServerPatApi': 'Developer Tools',
  // Communication
  'discordBotApi': 'Communication', 'discordWebhookApi': 'Communication',
  'mattermostApi': 'Communication', 'matrixApi': 'Communication',
  'rocketchatApi': 'Communication', 'zulipApi': 'Communication',
  'whatsAppApi': 'Communication', 'whatsAppTriggerApi': 'Communication',
  'vonageApi': 'Communication', 'messageBirdApi': 'Communication',
  'plivoApi': 'Communication', 'sms77Api': 'Communication',
  'moceanApi': 'Communication', 'msg91Api': 'Communication',
  'signl4Api': 'Communication', 'pushoverApi': 'Communication',
  'pushcutApi': 'Communication', 'pushbulletOAuth2Api': 'Communication',
  'gotifyApi': 'Communication', 'lineNotifyOAuth2Api': 'Communication',
  'twakeCloudApi': 'Communication', 'twakeServerApi': 'Communication',
  'twistOAuth2Api': 'Communication', 'ciscoWebexOAuth2Api': 'Communication',
  // Email
  'smtp': 'Email', 'imap': 'Email',
  'mailchimpApi': 'Email', 'mailchimpOAuth2Api': 'Email',
  'mailgunApi': 'Email', 'mailjetEmailApi': 'Email', 'mailjetSmsApi': 'Email',
  'mandrillApi': 'Email', 'mailerLiteApi': 'Email', 'mailcheckApi': 'Email',
  'brevoApi': 'Email', 'sendyApi': 'Email', 'postmarkApi': 'Email',
  'convertKitApi': 'Email', 'egoiApi': 'Email',
  // AI / LLM
  'perplexityApi': 'AI / LLM', 'jinaAiApi': 'AI / LLM', 'airtopApi': 'AI / LLM',
  'deepLApi': 'AI / LLM', 'lingvaNexApi': 'AI / LLM',
  // Databases
  'mongoDb': 'Databases', 'redis': 'Databases', 'snowflake': 'Databases',
  'crateDb': 'Databases', 'questDb': 'Databases', 'timescaleDb': 'Databases',
  'oracleDBApi': 'Databases', 'verticaApi': 'Databases',
  // Infrastructure
  'kafka': 'Infrastructure', 'rabbitMQ': 'Infrastructure', 'mqtt': 'Infrastructure',
  'amqp': 'Infrastructure', 'ldap': 'Infrastructure',
  'ftp': 'Infrastructure', 'sftp': 'Infrastructure',
  'sshPassword': 'Infrastructure', 'sshPrivateKey': 'Infrastructure',
  'homeAssistantApi': 'Infrastructure', 'cloudflareApi': 'Infrastructure',
  'n8nApi': 'Infrastructure',
  // CRM / Sales
  'salesforceOAuth2Api': 'CRM / Sales', 'salesforceJwtApi': 'CRM / Sales',
  'hubspotApi': 'CRM / Sales', 'hubspotAppToken': 'CRM / Sales',
  'hubspotDeveloperApi': 'CRM / Sales', 'pipedriveApi': 'CRM / Sales',
  'pipedriveOAuth2Api': 'CRM / Sales', 'copperApi': 'CRM / Sales',
  'freshworksCrmApi': 'CRM / Sales', 'agileCrmApi': 'CRM / Sales',
  'activeCampaignApi': 'CRM / Sales', 'highLevelApi': 'CRM / Sales',
  'highLevelOAuth2Api': 'CRM / Sales', 'mauticApi': 'CRM / Sales',
  'mauticOAuth2Api': 'CRM / Sales', 'keapOAuth2Api': 'CRM / Sales',
  'intercomApi': 'CRM / Sales', 'driftApi': 'CRM / Sales',
  'driftOAuth2Api': 'CRM / Sales', 'salesmateApi': 'CRM / Sales',
  'monicaCrmApi': 'CRM / Sales', 'haloPSAApi': 'CRM / Sales',
  'harvestApi': 'CRM / Sales', 'harvestOAuth2Api': 'CRM / Sales',
  // Project Management
  'clickUpApi': 'Project Management', 'clickUpOAuth2Api': 'Project Management',
  'asanaApi': 'Project Management', 'asanaOAuth2Api': 'Project Management',
  'trelloApi': 'Project Management', 'mondayComApi': 'Project Management',
  'mondayComOAuth2Api': 'Project Management', 'todoistApi': 'Project Management',
  'todoistOAuth2Api': 'Project Management', 'taigaApi': 'Project Management',
  'wekanApi': 'Project Management', 'flowApi': 'Project Management',
  'jotFormApi': 'Project Management', 'formstackApi': 'Project Management',
  'formstackOAuth2Api': 'Project Management', 'formIoApi': 'Project Management',
  // Marketing
  'getResponseApi': 'Marketing', 'getResponseOAuth2Api': 'Marketing',
  'customerIoApi': 'Marketing', 'iterableApi': 'Marketing',
  'segmentApi': 'Marketing', 'autopilotApi': 'Marketing',
  'lemlistApi': 'Marketing', 'emeliaApi': 'Marketing',
  'loneScaleApi': 'Marketing', 'onfleetApi': 'Marketing',
  'surveyMonkeyApi': 'Marketing', 'surveyMonkeyOAuth2Api': 'Marketing',
  'eventbriteApi': 'Marketing', 'eventbriteOAuth2Api': 'Marketing',
  'demioApi': 'Marketing', 'goToWebinarOAuth2Api': 'Marketing',
  'calApi': 'Marketing', 'calendlyApi': 'Marketing', 'calendlyOAuth2Api': 'Marketing',
  'acuitySchedulingApi': 'Marketing', 'acuitySchedulingOAuth2Api': 'Marketing',
  'beeminderApi': 'Marketing', 'beeminderOAuth2Api': 'Marketing',
  'typeformApi': 'Marketing', 'typeformOAuth2Api': 'Marketing',
  'gumroadApi': 'Marketing', 'tapfiliateApi': 'Marketing',
  'humanticAiApi': 'Marketing', 'hunterApi': 'Marketing',
  'uProcApi': 'Marketing', 'upleadApi': 'Marketing',
  'clearbitApi': 'Marketing', 'dropcontactApi': 'Marketing',
  'brandfetchApi': 'Marketing',
  // Social Media
  'facebookGraphApi': 'Social Media', 'facebookGraphAppApi': 'Social Media',
  'facebookLeadAdsOAuth2Api': 'Social Media',
  'linkedInOAuth2Api': 'Social Media', 'linkedInCommunityManagementOAuth2Api': 'Social Media',
  'redditOAuth2Api': 'Social Media', 'mediumApi': 'Social Media',
  'mediumOAuth2Api': 'Social Media', 'disqusApi': 'Social Media',
  'twitterOAuth1Api': 'Social Media', 'twitterOAuth2Api': 'Social Media',
  // Storage / Files
  'dropboxApi': 'Storage', 'boxOAuth2Api': 'Storage',
  'nextCloudApi': 'Storage', 'nextCloudOAuth2Api': 'Storage',
  // Productivity
  'notionOAuth2Api': 'Productivity', 'codaApi': 'Productivity',
  'airtableApi': 'Productivity', 'airtableOAuth2Api': 'Productivity',
  'airtableTokenApi': 'Productivity', 'gristApi': 'Productivity',
  'seaTableApi': 'Productivity', 'nocoDb': 'Productivity',
  'nocoDbApiToken': 'Productivity', 'baserowApi': 'Productivity',
  'stackbyApi': 'Productivity', 'quickBaseApi': 'Productivity',
  'cockpitApi': 'Productivity', 'storyblokContentApi': 'Productivity',
  'storyblokManagementApi': 'Productivity', 'strapiApi': 'Productivity',
  'strapiTokenApi': 'Productivity', 'contentfulApi': 'Productivity',
  'webflowApi': 'Productivity', 'webflowOAuth2Api': 'Productivity',
  'wordpressApi': 'Productivity', 'ghostAdminApi': 'Productivity',
  'ghostContentApi': 'Productivity', 'bubbleApi': 'Productivity',
  'figmaApi': 'Productivity', 'miroOAuth2Api': 'Productivity',
  'spotifyOAuth2Api': 'Productivity', 'philipsHueOAuth2Api': 'Productivity',
  'ouraApi': 'Productivity', 'stravaOAuth2Api': 'Productivity',
  'raindropOAuth2Api': 'Productivity',
  // Finance / E-commerce
  'stripeApi': 'Finance', 'payPalApi': 'Finance',
  'chargebeeApi': 'Finance', 'wooCommerceApi': 'Finance',
  'shopifyAccessTokenApi': 'Finance', 'shopifyApi': 'Finance',
  'shopifyOAuth2Api': 'Finance', 'quickBooksOAuth2Api': 'Finance',
  'xeroOAuth2Api': 'Finance', 'wiseApi': 'Finance', 'paddleApi': 'Finance',
  'invoiceNinjaApi': 'Finance', 'magento2Api': 'Finance',
  'unleashedSoftwareApi': 'Finance', 'erpNextApi': 'Finance',
  'odooApi': 'Finance', 'profitWellApi': 'Finance',
  'marketstackApi': 'Finance',
  // Analytics / Monitoring
  'datadogApi': 'Analytics', 'postHogApi': 'Analytics',
  'grafanaApi': 'Analytics', 'dynatraceApi': 'Analytics',
  'kibanaApi': 'Analytics', 'metabaseApi': 'Analytics',
  'clockifyApi': 'Analytics', 'togglApi': 'Analytics',
  'uptimeRobotApi': 'Analytics', 'currentsApi': 'Analytics',
  'nasaApi': 'Analytics', 'openWeatherMapApi': 'Analytics',
  'oneSimpleApi': 'Analytics', 'peekalinkApi': 'Analytics',
  'gongApi': 'Analytics',
  // Security
  'alienVaultApi': 'Security', 'carbonBlackApi': 'Security',
  'crowdStrikeOAuth2Api': 'Security', 'elasticSecurityApi': 'Security',
  'elasticsearchApi': 'Security', 'splunkApi': 'Security',
  'qRadarApi': 'Security', 'theHiveApi': 'Security', 'theHiveProjectApi': 'Security',
  'cortexApi': 'Security', 'mispApi': 'Security', 'virusTotalApi': 'Security',
  'filescanApi': 'Security', 'dfirIrisApi': 'Security', 'hybridAnalysisApi': 'Security',
  'recordedFutureApi': 'Security', 'securityScorecardApi': 'Security',
  'sekoiaApi': 'Security', 'fortiGateApi': 'Security',
  'ciscoSecureEndpointApi': 'Security', 'ciscoUmbrellaApi': 'Security',
  'ciscoMerakiApi': 'Security', 'f5BigIpApi': 'Security',
  'impervaWafApi': 'Security', 'netscalerAdcApi': 'Security',
  'qualysApi': 'Security', 'rapid7InsightVmApi': 'Security',
  'sysdigApi': 'Security', 'trellixEpoApi': 'Security',
  'venafiTlsProtectCloudApi': 'Security', 'venafiTlsProtectDatacenterApi': 'Security',
  'solarWindsIpamApi': 'Security', 'solarWindsObservabilityApi': 'Security',
  'zscalerZiaApi': 'Security', 'urlScanIoApi': 'Security',
  'malcoreApi': 'Security', 'mistApi': 'Security', 'openCTIApi': 'Security',
  // Service / Support
  'freshdeskApi': 'Support', 'freshserviceApi': 'Support',
  'zendeskApi': 'Support', 'zendeskOAuth2Api': 'Support',
  'helpScoutOAuth2Api': 'Support', 'serviceNowBasicApi': 'Support',
  'serviceNowOAuth2Api': 'Support', 'syncroMspApi': 'Support',
  'workableApi': 'Support', 'bambooHrApi': 'Support',
  'pagerDutyApi': 'Support', 'pagerDutyOAuth2Api': 'Support',
  'sentryIoApi': 'Support', 'sentryIoOAuth2Api': 'Support',
  'sentryIoServerApi': 'Support',
  // Other
  'actionNetworkApi': 'Other', 'adaloApi': 'Other', 'affinityApi': 'Other',
  'apiTemplateIoApi': 'Other', 'auth0ManagementApi': 'Other', 'oktaApi': 'Other',
  'bannerbearApi': 'Other', 'bitlyApi': 'Other', 'bitlyOAuth2Api': 'Other',
  'bitwardenApi': 'Other', 'convertApi': 'Other', 'dhlApi': 'Other',
  'discourseApi': 'Other', 'fileMaker': 'Other', 'koBoToolboxApi': 'Other',
  'mindeeInvoiceApi': 'Other', 'mindeeReceiptApi': 'Other',
  'orbitApi': 'Other', 'phantombusterApi': 'Other',
  'shufflerApi': 'Other', 'veroApi': 'Other', 'wufooApi': 'Other',
  'yourlsApi': 'Other', 'zammadBasicAuthApi': 'Other', 'zammadTokenAuthApi': 'Other',
  'zohoOAuth2Api': 'Other', 'zoomApi': 'Other', 'zoomOAuth2Api': 'Other',
  'supabaseApi': 'Other',
};

// All n8n credential names (from the GitHub listing)
const allCreds = [
  'ActionNetworkApi','ActiveCampaignApi','AcuitySchedulingApi','AcuitySchedulingOAuth2Api',
  'AdaloApi','AffinityApi','AgileCrmApi','AirtableApi','AirtableOAuth2Api','AirtableTokenApi',
  'AirtopApi','AlienVaultApi','Amqp','ApiTemplateIoApi','AsanaApi','AsanaOAuth2Api',
  'Auth0ManagementApi','AutopilotApi','Aws','AwsAssumeRole',
  'AzureStorageOAuth2Api','AzureStorageSharedKeyApi',
  'BambooHrApi','BannerbearApi','BaserowApi','BeeminderApi','BeeminderOAuth2Api',
  'BitbucketAccessTokenApi','BitbucketApi','BitlyApi','BitlyOAuth2Api','BitwardenApi',
  'BoxOAuth2Api','BrandfetchApi','BrevoApi','BubbleApi',
  'CalApi','CalendlyApi','CalendlyOAuth2Api','CarbonBlackApi','ChargebeeApi',
  'CircleCiApi','CiscoMerakiApi','CiscoSecureEndpointApi','CiscoUmbrellaApi','CiscoWebexOAuth2Api',
  'ClearbitApi','ClickUpApi','ClickUpOAuth2Api','ClockifyApi','CloudflareApi',
  'CockpitApi','CodaApi','ContentfulApi','ConvertApi','ConvertKitApi',
  'CopperApi','CortexApi','CrateDb','CrowdStrikeOAuth2Api','Crypto',
  'CurrentsApi','CustomerIoApi',
  'DatadogApi','DeepLApi','DemioApi','DfirIrisApi','DhlApi',
  'DiscordBotApi','DiscordOAuth2Api','DiscordWebhookApi','DiscourseApi','DisqusApi',
  'DriftApi','DriftOAuth2Api','DropboxApi','DropboxOAuth2Api','DropcontactApi','DynatraceApi',
  'ERPNextApi','EgoiApi','ElasticSecurityApi','ElasticsearchApi','EmeliaApi',
  'EventbriteApi','EventbriteOAuth2Api',
  'F5BigIpApi','FacebookGraphApi','FacebookGraphAppApi','FacebookLeadAdsOAuth2Api',
  'FigmaApi','FileMaker','FilescanApi','FlowApi','FormIoApi',
  'FormstackApi','FormstackOAuth2Api','FortiGateApi','FreshdeskApi','FreshserviceApi','FreshworksCrmApi',
  'Ftp',
  'GSuiteAdminOAuth2Api','GetResponseApi','GetResponseOAuth2Api',
  'GhostAdminApi','GhostContentApi','GitPassword',
  'GithubApi','GithubOAuth2Api','GitlabApi','GitlabOAuth2Api',
  'GmailOAuth2Api','GoToWebinarOAuth2Api','GongApi','GongOAuth2Api',
  'GoogleAdsOAuth2Api','GoogleAnalyticsOAuth2Api','GoogleApi',
  'GoogleBigQueryOAuth2Api','GoogleBooksOAuth2Api','GoogleBusinessProfileOAuth2Api',
  'GoogleCalendarOAuth2Api','GoogleChatOAuth2Api','GoogleCloudNaturalLanguageOAuth2Api',
  'GoogleCloudStorageOAuth2Api','GoogleContactsOAuth2Api','GoogleDocsOAuth2Api',
  'GoogleDriveOAuth2Api','GoogleFirebaseCloudFirestoreOAuth2Api',
  'GoogleFirebaseRealtimeDatabaseOAuth2Api','GoogleOAuth2Api',
  'GooglePerspectiveOAuth2Api','GoogleSheetsOAuth2Api','GoogleSheetsTriggerOAuth2Api',
  'GoogleSlidesOAuth2Api','GoogleTasksOAuth2Api','GoogleTranslateOAuth2Api',
  'GotifyApi','GrafanaApi','GristApi','GumroadApi',
  'HaloPSAApi','HarvestApi','HarvestOAuth2Api','HelpScoutOAuth2Api',
  'HighLevelApi','HighLevelOAuth2Api','HomeAssistantApi',
  'HttpBasicAuth','HttpBearerAuth','HttpCustomAuth','HttpDigestAuth',
  'HttpHeaderAuth','HttpMultipleHeadersAuth','HttpQueryAuth','HttpSslAuth',
  'HubspotApi','HubspotAppToken','HubspotDeveloperApi','HubspotOAuth2Api',
  'HumanticAiApi','HunterApi','HybridAnalysisApi',
  'Imap','ImpervaWafApi','IntercomApi','InvoiceNinjaApi','IterableApi',
  'JenkinsApi','JinaAiApi','JiraSoftwareCloudApi','JiraSoftwareServerApi','JiraSoftwareServerPatApi',
  'JotFormApi','JwtAuth',
  'Kafka','KeapOAuth2Api','KibanaApi','KoBoToolboxApi',
  'Ldap','LemlistApi','LineNotifyOAuth2Api','LinearApi','LinearOAuth2Api',
  'LingvaNexApi','LinkedInCommunityManagementOAuth2Api','LinkedInOAuth2Api','LoneScaleApi',
  'Magento2Api','MailcheckApi','MailchimpApi','MailchimpOAuth2Api',
  'MailerLiteApi','MailgunApi','MailjetEmailApi','MailjetSmsApi',
  'MalcoreApi','MandrillApi','MarketstackApi','MatrixApi','MattermostApi',
  'MauticApi','MauticOAuth2Api','MediumApi','MediumOAuth2Api','MessageBirdApi','MetabaseApi',
  'MicrosoftAzureCosmosDbSharedKeyApi','MicrosoftAzureMonitorOAuth2Api',
  'MicrosoftDynamicsOAuth2Api','MicrosoftEntraOAuth2Api','MicrosoftExcelOAuth2Api',
  'MicrosoftGraphSecurityOAuth2Api','MicrosoftOAuth2Api',
  'MicrosoftOneDriveOAuth2Api','MicrosoftOutlookOAuth2Api','MicrosoftSharePointOAuth2Api',
  'MicrosoftSql','MicrosoftTeamsOAuth2Api','MicrosoftToDoOAuth2Api',
  'MindeeInvoiceApi','MindeeReceiptApi','MiroOAuth2Api',
  'MispApi','MistApi','MoceanApi','MondayComApi','MondayComOAuth2Api',
  'MongoDb','MonicaCrmApi','Mqtt','Msg91Api','MySql',
  'N8nApi','NasaApi','NetlifyApi','NetscalerAdcApi','NextCloudApi','NextCloudOAuth2Api',
  'NocoDb','NocoDbApiToken','NotionApi','NotionOAuth2Api','NpmApi',
  'OAuth1Api','OAuth2Api','OdooApi','OktaApi','OneSimpleApi','OnfleetApi',
  'OpenAiApi','OpenCTIApi','OpenWeatherMapApi','OracleDBApi','OrbitApi','OuraApi',
  'PaddleApi','PagerDutyApi','PagerDutyOAuth2Api','PayPalApi','PeekalinkApi',
  'PerplexityApi','PhantombusterApi','PhilipsHueOAuth2Api',
  'PipedriveApi','PipedriveOAuth2Api','PlivoApi','PostHogApi',
  'Postgres','PostmarkApi','ProfitWellApi','PushbulletOAuth2Api','PushcutApi','PushoverApi',
  'QRadarApi','QualysApi','QuestDb','QuickBaseApi','QuickBooksOAuth2Api',
  'RabbitMQ','RaindropOAuth2Api','Rapid7InsightVmApi','RecordedFutureApi',
  'RedditOAuth2Api','Redis','RocketchatApi','RundeckApi',
  'S3','SalesforceJwtApi','SalesforceOAuth2Api','SalesmateApi',
  'SeaTableApi','SecurityScorecardApi','SegmentApi','SekoiaApi',
  'SendGridApi','SendyApi','SentryIoApi','SentryIoOAuth2Api','SentryIoServerApi',
  'ServiceNowBasicApi','ServiceNowOAuth2Api',
  'Sftp','ShopifyAccessTokenApi','ShopifyApi','ShopifyOAuth2Api',
  'ShufflerApi','Signl4Api','SlackApi','SlackOAuth2Api','Sms77Api',
  'Smtp','Snowflake','SolarWindsIpamApi','SolarWindsObservabilityApi','SplunkApi',
  'SpotifyOAuth2Api','SshPassword','SshPrivateKey',
  'StackbyApi','StoryblokContentApi','StoryblokManagementApi',
  'StrapiApi','StrapiTokenApi','StravaOAuth2Api','StripeApi','SupabaseApi',
  'SurveyMonkeyApi','SurveyMonkeyOAuth2Api','SyncroMspApi','SysdigApi',
  'TaigaApi','TapfiliateApi','TelegramApi','TheHiveApi','TheHiveProjectApi',
  'TimescaleDb','TodoistApi','TodoistOAuth2Api','TogglApi','TotpApi',
  'TravisCiApi','TrellixEpoApi','TrelloApi','TwakeCloudApi','TwakeServerApi',
  'TwilioApi','TwistOAuth2Api','TwitterOAuth1Api','TwitterOAuth2Api',
  'TypeformApi','TypeformOAuth2Api',
  'UProcApi','UnleashedSoftwareApi','UpleadApi','UptimeRobotApi','UrlScanIoApi',
  'VenafiTlsProtectCloudApi','VenafiTlsProtectDatacenterApi','VeroApi','VerticaApi',
  'VirusTotalApi','VonageApi',
  'WebflowApi','WebflowOAuth2Api','WekanApi','WhatsAppApi','WhatsAppTriggerApi',
  'WiseApi','WooCommerceApi','WordpressApi','WorkableApi','WufooApi',
  'XeroOAuth2Api','YouTubeOAuth2Api','YourlsApi',
  'ZabbixApi','ZammadBasicAuthApi','ZammadTokenAuthApi',
  'ZendeskApi','ZendeskOAuth2Api','ZohoOAuth2Api','ZoomApi','ZoomOAuth2Api',
  'ZscalerZiaApi','ZulipApi'
];

function pascalToType(name) {
  // ERPNextApi -> erpNextApi, GSuiteAdminOAuth2Api -> gSuiteAdminOAuth2Api
  if (name.length <= 1) return name.toLowerCase();
  // Handle special cases
  if (name === 'ERPNextApi') return 'erpNextApi';
  if (name === 'GSuiteAdminOAuth2Api') return 'gSuiteAdminOAuth2Api';
  if (name === 'OAuth1Api') return 'oAuth1Api';
  if (name === 'OAuth2Api') return 'oAuth2Api';
  if (name === 'RabbitMQ') return 'rabbitmq';
  if (name === 'UProcApi') return 'uProcApi';
  return name.charAt(0).toLowerCase() + name.slice(1);
}

function typeToDisplayName(pascalName) {
  // Convert PascalCase to display name
  let name = pascalName
    .replace(/OAuth2Api$/, ' OAuth2 API')
    .replace(/OAuth1Api$/, ' OAuth1 API')
    .replace(/Api$/, ' API')
    .replace(/Db$/, ' DB')
    .replace(/([A-Z])/g, ' $1')
    .trim()
    .replace(/  +/g, ' ');
  // Fix common names
  name = name.replace('G Suite', 'Google Workspace')
    .replace('Git Hub', 'GitHub').replace('Git Lab', 'GitLab')
    .replace('Git Password', 'Git Password')
    .replace('You Tube', 'YouTube').replace('Click Up', 'ClickUp')
    .replace('Drop Box', 'Dropbox').replace('Drop contact', 'Dropcontact')
    .replace('Pay Pal', 'PayPal').replace('Quick Books', 'QuickBooks')
    .replace('Post Hog', 'PostHog').replace('Send Grid', 'SendGrid')
    .replace('Mail chimp', 'Mailchimp').replace('Mail gun', 'Mailgun')
    .replace('Mail jet', 'Mailjet').replace('Mailer Lite', 'MailerLite')
    .replace('Mail check', 'Mailcheck')
    .replace('Linked In', 'LinkedIn').replace('Bit Bucket', 'Bitbucket')
    .replace('Bit Ly', 'Bitly').replace('Bit Warden', 'Bitwarden')
    .replace('What s App', 'WhatsApp').replace('Whats App', 'WhatsApp')
    .replace('Woo Commerce', 'WooCommerce').replace('Word Press', 'WordPress')
    .replace('Web flow', 'Webflow').replace('We Kan', 'Wekan')
    .replace('Monday Com', 'Monday.com').replace('Go To Webinar', 'GoToWebinar')
    .replace('No Co', 'NocoDB').replace('Noco Db', 'NocoDB')
    .replace('Crowd Strike', 'CrowdStrike').replace('Virus Total', 'VirusTotal')
    .replace('Help Scout', 'HelpScout').replace('High Level', 'HighLevel')
    .replace('Home Assistant', 'Home Assistant').replace('Pager Duty', 'PagerDuty')
    .replace('Survey Monkey', 'SurveyMonkey').replace('Event Brite', 'Eventbrite')
    .replace('Hub Spot', 'HubSpot').replace('Hub spot', 'HubSpot')
    .replace('Circle Ci', 'CircleCI').replace('Travis Ci', 'TravisCI')
    .replace('Elastic Search', 'Elasticsearch').replace('Elastic search', 'Elasticsearch')
    .replace('Active Campaign', 'ActiveCampaign').replace('Convert Kit', 'ConvertKit')
    .replace('Face Book', 'Facebook').replace('Facebook Graph App', 'Facebook Graph App')
    .replace('Fresh Desk', 'Freshdesk').replace('Fresh Service', 'Freshservice')
    .replace('Fresh Works Crm', 'Freshworks CRM')
    .replace('Customer Io', 'Customer.io').replace('Sentry Io', 'Sentry.io')
    .replace('Form Io', 'Form.io').replace('Form stack', 'Formstack')
    .replace('Jot Form', 'JotForm').replace('Jira Software Cloud', 'Jira Cloud')
    .replace('Jira Software Server Pat', 'Jira Server (PAT)')
    .replace('Jira Software Server', 'Jira Server')
    .replace('Ko Bo Toolbox', 'KoBoToolbox')
    .replace('Monday Com', 'Monday.com')
    .replace('Sea Table', 'SeaTable').replace('Story Blok', 'Storyblok')
    .replace('One Drive', 'OneDrive').replace('Share Point', 'SharePoint')
    .replace('Quick Base', 'QuickBase').replace('Rabbit M Q', 'RabbitMQ')
    .replace('Philips Hue', 'Philips Hue').replace('Push Bullet', 'Pushbullet')
    .replace('Push Cut', 'Pushcut').replace('Push Over', 'Pushover')
    .replace('Open Weather Map', 'OpenWeatherMap').replace('Open C T I', 'OpenCTI')
    .replace('Halo P S A', 'HaloPSA')
    .replace('D H L', 'DHL').replace('E R P Next', 'ERPNext')
    .replace('Solar Winds', 'SolarWinds').replace('Carbon Black', 'Carbon Black')
    .replace('Record Ed Future', 'Recorded Future')
    .replace('Recorded Future', 'Recorded Future')
    .replace('Rapid 7', 'Rapid7').replace('Trellix Epo', 'Trellix ePO')
    .replace('Zscaler Zia', 'Zscaler ZIA').replace('F 5 Big Ip', 'F5 BIG-IP')
    .replace('Imperva Waf', 'Imperva WAF').replace('Netscaler Adc', 'NetScaler ADC')
    .replace('Deep L', 'DeepL').replace('Jina Ai', 'Jina AI')
    .replace('Lingva Nex', 'LingvaNex')
    .replace('File Maker', 'FileMaker').replace('File Scan', 'Filescan')
    .replace('Dfir Iris', 'DFIR-IRIS').replace('Hybrid Analysis', 'Hybrid Analysis')
    .replace('N 8n', 'n8n').replace('Npm', 'npm')
    .replace('Security Scorecard', 'SecurityScorecard')
    .replace('Url Scan Io', 'URLScan.io')
    .replace('Sms 77', 'sms77').replace('Msg 91', 'MSG91')
    .replace('Cisco Meraki', 'Cisco Meraki').replace('Cisco Secure Endpoint', 'Cisco Secure Endpoint')
    .replace('Cisco Umbrella', 'Cisco Umbrella').replace('Cisco Webex', 'Cisco Webex')
    .replace('Monica Crm', 'Monica CRM')
    .replace('Bamboo Hr', 'BambooHR').replace('Microsoft Azure Cosmos Db', 'Azure Cosmos DB')
    .replace('Microsoft Azure Monitor', 'Azure Monitor')
    .replace('Microsoft Dynamics', 'Microsoft Dynamics 365')
    .replace('Microsoft Entra', 'Microsoft Entra ID')
    .replace('Microsoft Graph Security', 'Microsoft Graph Security')
    .replace('Microsoft Sql', 'Microsoft SQL Server')
    .replace('Microsoft To Do', 'Microsoft To Do')
    .replace('Syncro Msp', 'Syncro MSP')
    .replace('Airtop', 'Airtop')
    .replace('Oracle D B', 'Oracle DB');
  return name;
}

function isOAuth2(type) {
  return type.endsWith('OAuth2Api') || type.endsWith('OAuth1Api');
}

function isInfraType(type) {
  return ['amqp','redis','snowflake','crateDb','questDb','timescaleDb','kafka',
    'rabbitMQ','mqtt','ldap','ftp','sftp','smtp','imap','mongoDb',
    'microsoftSql','sshPassword','sshPrivateKey'].includes(type) ||
    ['rabbitmq'].includes(type);
}

// Infrastructure-specific field templates
const infraFields = {
  'amqp': [
    {n:'hostname',d:'Hostname',t:'STRING',def:'localhost'},
    {n:'port',d:'Port',t:'NUMBER',def:5672},
    {n:'username',d:'Username',t:'STRING'},
    {n:'password',d:'Password',t:'STRING',pw:true}
  ],
  'redis': [
    {n:'host',d:'Host',t:'STRING',def:'localhost'},
    {n:'port',d:'Port',t:'NUMBER',def:6379},
    {n:'password',d:'Password',t:'STRING',pw:true},
    {n:'database',d:'Database Number',t:'NUMBER',def:0},
    {n:'ssl',d:'SSL',t:'BOOLEAN',def:false}
  ],
  'snowflake': [
    {n:'account',d:'Account',t:'STRING',req:true},
    {n:'database',d:'Database',t:'STRING',req:true},
    {n:'warehouse',d:'Warehouse',t:'STRING'},
    {n:'username',d:'Username',t:'STRING',req:true},
    {n:'password',d:'Password',t:'STRING',pw:true,req:true},
    {n:'schema',d:'Schema',t:'STRING'},
    {n:'role',d:'Role',t:'STRING'}
  ],
  'crateDb': [
    {n:'host',d:'Host',t:'STRING',def:'localhost'},
    {n:'port',d:'Port',t:'NUMBER',def:5432},
    {n:'database',d:'Database',t:'STRING'},
    {n:'username',d:'Username',t:'STRING'},
    {n:'password',d:'Password',t:'STRING',pw:true},
    {n:'ssl',d:'SSL',t:'BOOLEAN',def:false}
  ],
  'questDb': [
    {n:'host',d:'Host',t:'STRING',def:'localhost'},
    {n:'port',d:'Port',t:'NUMBER',def:8812},
    {n:'database',d:'Database',t:'STRING'},
    {n:'username',d:'Username',t:'STRING'},
    {n:'password',d:'Password',t:'STRING',pw:true}
  ],
  'timescaleDb': [
    {n:'host',d:'Host',t:'STRING',def:'localhost'},
    {n:'port',d:'Port',t:'NUMBER',def:5432},
    {n:'database',d:'Database',t:'STRING',req:true},
    {n:'username',d:'Username',t:'STRING',req:true},
    {n:'password',d:'Password',t:'STRING',pw:true,req:true},
    {n:'ssl',d:'SSL',t:'BOOLEAN',def:false}
  ],
  'kafka': [
    {n:'clientId',d:'Client ID',t:'STRING'},
    {n:'brokers',d:'Brokers',t:'STRING',req:true,ph:'kafka1:9092,kafka2:9092'},
    {n:'ssl',d:'SSL',t:'BOOLEAN',def:true},
    {n:'username',d:'Username',t:'STRING'},
    {n:'password',d:'Password',t:'STRING',pw:true}
  ],
  'rabbitmq': [
    {n:'hostname',d:'Hostname',t:'STRING',def:'localhost'},
    {n:'port',d:'Port',t:'NUMBER',def:5672},
    {n:'username',d:'Username',t:'STRING'},
    {n:'password',d:'Password',t:'STRING',pw:true},
    {n:'vhost',d:'Virtual Host',t:'STRING',def:'/'},
    {n:'ssl',d:'SSL',t:'BOOLEAN',def:false}
  ],
  'mqtt': [
    {n:'host',d:'Host',t:'STRING',req:true},
    {n:'port',d:'Port',t:'NUMBER',def:1883},
    {n:'username',d:'Username',t:'STRING'},
    {n:'password',d:'Password',t:'STRING',pw:true},
    {n:'ssl',d:'SSL',t:'BOOLEAN',def:false}
  ],
  'ldap': [
    {n:'hostname',d:'LDAP Server Address',t:'STRING',req:true},
    {n:'port',d:'Port',t:'NUMBER',def:389},
    {n:'bindDN',d:'Bind DN',t:'STRING'},
    {n:'bindPassword',d:'Bind Password',t:'STRING',pw:true}
  ],
  'ftp': [
    {n:'host',d:'Host',t:'STRING',req:true},
    {n:'port',d:'Port',t:'NUMBER',def:21},
    {n:'username',d:'Username',t:'STRING'},
    {n:'password',d:'Password',t:'STRING',pw:true}
  ],
  'sftp': [
    {n:'host',d:'Host',t:'STRING',req:true},
    {n:'port',d:'Port',t:'NUMBER',def:22},
    {n:'username',d:'Username',t:'STRING',req:true},
    {n:'password',d:'Password',t:'STRING',pw:true}
  ],
  'smtp': [
    {n:'user',d:'User',t:'STRING'},
    {n:'password',d:'Password',t:'STRING',pw:true},
    {n:'host',d:'Host',t:'STRING',req:true},
    {n:'port',d:'Port',t:'NUMBER',def:465},
    {n:'secure',d:'SSL/TLS',t:'BOOLEAN',def:true}
  ],
  'imap': [
    {n:'user',d:'User',t:'STRING',req:true},
    {n:'password',d:'Password',t:'STRING',pw:true,req:true},
    {n:'host',d:'Host',t:'STRING',req:true},
    {n:'port',d:'Port',t:'NUMBER',def:993},
    {n:'secure',d:'SSL/TLS',t:'BOOLEAN',def:true}
  ],
  'mongoDb': [
    {n:'host',d:'Host',t:'STRING',def:'localhost'},
    {n:'port',d:'Port',t:'NUMBER',def:27017},
    {n:'database',d:'Database',t:'STRING',req:true},
    {n:'username',d:'Username',t:'STRING'},
    {n:'password',d:'Password',t:'STRING',pw:true},
    {n:'tls',d:'TLS',t:'BOOLEAN',def:false}
  ],
  'microsoftSql': [
    {n:'server',d:'Server',t:'STRING',def:'localhost'},
    {n:'port',d:'Port',t:'NUMBER',def:1433},
    {n:'database',d:'Database',t:'STRING',def:'master'},
    {n:'user',d:'User',t:'STRING',def:'sa'},
    {n:'password',d:'Password',t:'STRING',pw:true},
    {n:'tls',d:'Encrypt',t:'BOOLEAN',def:true}
  ],
  'sshPassword': [
    {n:'host',d:'Host',t:'STRING',req:true},
    {n:'port',d:'Port',t:'NUMBER',def:22},
    {n:'username',d:'Username',t:'STRING',req:true},
    {n:'password',d:'Password',t:'STRING',pw:true,req:true}
  ],
  'sshPrivateKey': [
    {n:'host',d:'Host',t:'STRING',req:true},
    {n:'port',d:'Port',t:'NUMBER',def:22},
    {n:'username',d:'Username',t:'STRING',req:true},
    {n:'privateKey',d:'Private Key',t:'STRING',pw:true,req:true},
    {n:'passphrase',d:'Passphrase',t:'STRING',pw:true}
  ],
};

// Special non-API types with specific fields
const specialFields = {
  'httpBearerAuth': [
    {n:'token',d:'Bearer Token',t:'STRING',pw:true,req:true}
  ],
  'httpCustomAuth': [
    {n:'json',d:'JSON Configuration',t:'STRING',req:true}
  ],
  'httpDigestAuth': [
    {n:'username',d:'Username',t:'STRING',req:true},
    {n:'password',d:'Password',t:'STRING',pw:true,req:true}
  ],
  'httpMultipleHeadersAuth': [
    {n:'json',d:'Headers JSON',t:'STRING',req:true}
  ],
  'httpSslAuth': [
    {n:'cert',d:'Certificate (PEM)',t:'STRING',req:true},
    {n:'key',d:'Private Key (PEM)',t:'STRING',pw:true,req:true},
    {n:'passphrase',d:'Passphrase',t:'STRING',pw:true}
  ],
  'jwtAuth': [
    {n:'keyType',d:'Key Type',t:'STRING',def:'passphrase'},
    {n:'secret',d:'Secret / Private Key',t:'STRING',pw:true,req:true},
    {n:'algorithm',d:'Algorithm',t:'STRING',def:'HS256'}
  ],
  'oAuth1Api': [
    {n:'consumerKey',d:'Consumer Key',t:'STRING',req:true},
    {n:'consumerSecret',d:'Consumer Secret',t:'STRING',pw:true,req:true},
    {n:'requestTokenUrl',d:'Request Token URL',t:'STRING',req:true},
    {n:'authorizationUrl',d:'Authorization URL',t:'STRING',req:true},
    {n:'accessTokenUrl',d:'Access Token URL',t:'STRING',req:true}
  ],
  'crypto': [
    {n:'value',d:'Value',t:'STRING',pw:true,req:true},
    {n:'algorithm',d:'Algorithm',t:'STRING',def:'sha256'}
  ],
  'totpApi': [
    {n:'secret',d:'Secret',t:'STRING',pw:true,req:true},
    {n:'label',d:'Label',t:'STRING'}
  ],
  'gitPassword': [
    {n:'username',d:'Username',t:'STRING',req:true},
    {n:'password',d:'Password / Token',t:'STRING',pw:true,req:true}
  ],
  'awsAssumeRole': [
    {n:'accessKeyId',d:'Access Key ID',t:'STRING',req:true},
    {n:'secretAccessKey',d:'Secret Access Key',t:'STRING',pw:true,req:true},
    {n:'roleArn',d:'Role ARN',t:'STRING',req:true},
    {n:'region',d:'Region',t:'STRING',def:'us-east-1'}
  ],
  'azureStorageSharedKeyApi': [
    {n:'accountName',d:'Account Name',t:'STRING',req:true},
    {n:'accountKey',d:'Account Key',t:'STRING',pw:true,req:true}
  ],
  'microsoftAzureCosmosDbSharedKeyApi': [
    {n:'endpoint',d:'Endpoint',t:'STRING',req:true},
    {n:'accountKey',d:'Account Key',t:'STRING',pw:true,req:true}
  ],
  'hubspotAppToken': [
    {n:'appToken',d:'App Token',t:'STRING',pw:true,req:true}
  ],
  'hubspotDeveloperApi': [
    {n:'developerApiKey',d:'Developer API Key',t:'STRING',pw:true,req:true}
  ],
  'nocoDb': [
    {n:'apiToken',d:'API Token',t:'STRING',pw:true,req:true},
    {n:'host',d:'Host',t:'STRING',def:'https://app.nocodb.com'}
  ],
  'nocoDbApiToken': [
    {n:'apiToken',d:'API Token',t:'STRING',pw:true,req:true},
    {n:'host',d:'Host',t:'STRING',def:'https://app.nocodb.com'}
  ],
  'fileMaker': [
    {n:'host',d:'Host',t:'STRING',req:true},
    {n:'database',d:'Database',t:'STRING',req:true},
    {n:'username',d:'Username',t:'STRING',req:true},
    {n:'password',d:'Password',t:'STRING',pw:true,req:true}
  ],
  'shopifyAccessTokenApi': [
    {n:'accessToken',d:'Access Token',t:'STRING',pw:true,req:true},
    {n:'shopSubdomain',d:'Shop Subdomain',t:'STRING',req:true}
  ],
  'twitterOAuth1Api': [
    {n:'consumerKey',d:'Consumer Key',t:'STRING',req:true},
    {n:'consumerSecret',d:'Consumer Secret',t:'STRING',pw:true,req:true},
    {n:'accessToken',d:'Access Token',t:'STRING',req:true},
    {n:'accessTokenSecret',d:'Access Token Secret',t:'STRING',pw:true,req:true}
  ],
  'serviceNowBasicApi': [
    {n:'user',d:'Username',t:'STRING',req:true},
    {n:'password',d:'Password',t:'STRING',pw:true,req:true},
    {n:'subdomain',d:'Instance Subdomain',t:'STRING',req:true}
  ],
  'zammadBasicAuthApi': [
    {n:'baseUrl',d:'Base URL',t:'STRING',req:true},
    {n:'username',d:'Username',t:'STRING',req:true},
    {n:'password',d:'Password',t:'STRING',pw:true,req:true}
  ],
  'zammadTokenAuthApi': [
    {n:'baseUrl',d:'Base URL',t:'STRING',req:true},
    {n:'accessToken',d:'Access Token',t:'STRING',pw:true,req:true}
  ],
  'salesforceJwtApi': [
    {n:'clientId',d:'Client ID',t:'STRING',req:true},
    {n:'privateKey',d:'Private Key',t:'STRING',pw:true,req:true},
    {n:'username',d:'Username',t:'STRING',req:true},
    {n:'environment',d:'Environment',t:'STRING',def:'production'}
  ],
  'sentryIoServerApi': [
    {n:'url',d:'Sentry Server URL',t:'STRING',req:true},
    {n:'apiToken',d:'API Token',t:'STRING',pw:true,req:true}
  ],
  'strapiTokenApi': [
    {n:'apiToken',d:'API Token',t:'STRING',pw:true,req:true},
    {n:'url',d:'URL',t:'STRING',def:'http://localhost:1337'}
  ],
  'airtableTokenApi': [
    {n:'apiToken',d:'Personal Access Token',t:'STRING',pw:true,req:true}
  ],
};

function generateField(f) {
  let code = `                NodeParameter.builder()\n`;
  code += `                        .name("${f.n}").displayName("${f.d}")\n`;
  code += `                        .type(ParameterType.${f.t || 'STRING'})`;
  if (f.req) code += `.required(true)`;
  if (f.def !== undefined) {
    if (typeof f.def === 'string') code += `\n                        .defaultValue("${f.def}")`;
    else if (typeof f.def === 'boolean') code += `\n                        .defaultValue(${f.def})`;
    else code += `\n                        .defaultValue(${f.def})`;
  }
  if (f.pw) code += `\n                        .typeOptions(Map.of("password", true))`;
  if (f.ph) code += `\n                        .placeHolder("${f.ph}")`;
  code += `.build()`;
  return code;
}

function generateApiKeyProvider(className, type, displayName, category, icon) {
  return `package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "${type}",
        displayName = "${displayName}",
        description = "${displayName} authentication",
        category = "${category}"${icon ? `,\n        icon = "${icon}"` : ''}
)
public class ${className} implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
                NodeParameter.builder()
                        .name("apiKey").displayName("API Key")
                        .type(ParameterType.STRING).required(true)
                        .typeOptions(Map.of("password", true)).build()
        );
    }
}
`;
}

function generateOAuth2Provider(className, type, displayName, category, icon, authUrl, tokenUrl) {
  let overrides = '';
  if (authUrl || tokenUrl) {
    let entries = [];
    if (authUrl) {
      entries.push(`                "authorizationUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("${authUrl}")
                        .build()`);
    }
    if (tokenUrl) {
      entries.push(`                "accessTokenUrl", NodeParameter.builder()
                        .type(ParameterType.STRING)
                        .defaultValue("${tokenUrl}")
                        .build()`);
    }
    overrides = `

    @Override
    public Map<String, NodeParameter> getPropertyOverrides() {
        return Map.of(
${entries.join(',\n')}
        );
    }`;
  }

  return `package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "${type}",
        displayName = "${displayName}",
        description = "${displayName} authentication",
        category = "${category}"${icon ? `,\n        icon = "${icon}"` : ''},
        extendsType = "oAuth2Api"
)
public class ${className} implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of();
    }${overrides}
}
`;
}

function generateCustomProvider(className, type, displayName, category, icon, fields) {
  const fieldCode = fields.map(f => generateField(f)).join(',\n');

  return `package io.trellis.credentials.impl;

import io.trellis.credentials.CredentialProviderInterface;
import io.trellis.credentials.annotation.CredentialProvider;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.List;
import java.util.Map;

@CredentialProvider(
        type = "${type}",
        displayName = "${displayName}",
        description = "${displayName} authentication",
        category = "${category}"${icon ? `,\n        icon = "${icon}"` : ''}
)
public class ${className} implements CredentialProviderInterface {

    @Override
    public List<NodeParameter> getProperties() {
        return List.of(
${fieldCode}
        );
    }
}
`;
}

let created = 0, skipped = 0;

for (const pascalName of allCreds) {
  const type = pascalToType(pascalName);

  // Skip existing
  if (existing.has(type)) { skipped++; continue; }
  // Also match normalized versions
  const normalizedExisting = [...existing].map(e => e.toLowerCase());
  if (normalizedExisting.includes(type.toLowerCase())) { skipped++; continue; }

  const className = pascalName + 'Credentials';
  const displayName = typeToDisplayName(pascalName);
  const category = categories[type] || 'Other';
  const icon = pascalName.toLowerCase().replace(/oauth2api$/,'').replace(/oauth1api$/,'').replace(/api$/,'').replace(/db$/,'');
  const filePath = path.join(outDir, className + '.java');

  // Skip if file already exists
  if (fs.existsSync(filePath)) { skipped++; continue; }

  let content;

  if (type.endsWith('OAuth2Api')) {
    // OAuth2 provider
    const urls = oauthUrls[type] || ['',''];
    content = generateOAuth2Provider(className, type, displayName, category, icon, urls[0], urls[1]);
  } else if (type === 'oAuth1Api' || type === 'twitterOAuth1Api') {
    // OAuth1 - special
    content = generateCustomProvider(className, type, displayName, category, icon, specialFields[type] || specialFields['oAuth1Api']);
  } else if (infraFields[type] || infraFields[type === 'rabbitmq' ? 'rabbitmq' : type]) {
    // Infrastructure type
    const fields = infraFields[type] || infraFields[type === 'rabbitmq' ? 'rabbitmq' : type];
    content = generateCustomProvider(className, type, displayName, category, icon, fields);
  } else if (specialFields[type]) {
    // Special fields
    content = generateCustomProvider(className, type, displayName, category, icon, specialFields[type]);
  } else {
    // Default: API key provider
    content = generateApiKeyProvider(className, type, displayName, category, icon);
  }

  fs.writeFileSync(filePath, content, 'utf8');
  created++;
}

console.log(`Created: ${created}, Skipped: ${skipped}`);
