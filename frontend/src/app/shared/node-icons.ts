import {
  // Already imported
  Globe, Merge, ArrowRight, Split, Clock, Play, Webhook, Reply,
  UnfoldVertical, Route, Pen, Code,
  CalendarClock, ListFilter, GitCompare, Lock, CopyMinus, FileText,
  Layers, Repeat, FileCode, ListEnd, ArrowUpNarrowWide, Replace, Sigma,
  Table2, Timer, ClipboardList, FileInput,
  Bot, Brain, Link, ScanText, Calculator, Search, BookOpen,
  Database, HardDriveUpload, ShieldCheck, Tag,
  // New imports
  Mail, Send, MessageSquare, MessageCircle, Users, Cloud, Bell, Bolt, Book,
  ChartBar, ChartLine, CloudSun, Cog, Eye, File, FileArchive, FileOutput,
  FileSpreadsheet, FileImage, Hash, Inbox, Key, Languages, Lightbulb,
  Newspaper, Palette, Rocket, Scissors, Terminal, Triangle, TriangleAlert,
  WandSparkles, Rss, GitBranch, CreditCard, ShoppingCart, ShoppingBag,
  DollarSign, LifeBuoy, LayoutDashboard, LayoutGrid, Calendar, Share2,
  Phone, Shield, Activity, House, Video, SquareCheck, Megaphone, Image,
  Truck, Bug, Briefcase, HardDrive, Building, Music, Bookmark, Package,
  Radio, ArrowLeftRight, Wifi, Monitor, Wrench, RefreshCw, Target, PenTool,
  Smartphone, Coins, AtSign, ThumbsUp, Sparkles, Contact, Settings,
  Workflow, IdCard,
  Type, FileJson, Gauge, StickyNote, Umbrella, RotateCw, Radar,
  LucideIconData,
} from 'lucide-angular';

// ---------------------------------------------------------------------------
// Custom brand icons not available in Lucide — defined in LucideIconData format
// ---------------------------------------------------------------------------

const Openai: LucideIconData = [
  ['path', { d: 'M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z' }],
  ['path', { d: 'M12 6v12' }],
  ['path', { d: 'M8 8l4 4 4-4' }],
];

const Anthropic: LucideIconData = [
  ['path', { d: 'M12 2L2 22h20L12 2z' }],
  ['path', { d: 'M12 8l5 10H7l5-10z' }],
];

const Google: LucideIconData = [
  ['circle', { cx: '12', cy: '12', r: '10' }],
  ['path', { d: 'M12 6a6 6 0 0 1 0 12 6 6 0 0 1-6-6h6V6z' }],
];

const Ollama: LucideIconData = [
  ['circle', { cx: '12', cy: '12', r: '10' }],
  ['circle', { cx: '12', cy: '12', r: '4' }],
];

const Mistral: LucideIconData = [
  ['rect', { x: '4', y: '4', width: '16', height: '16', rx: '2' }],
  ['path', { d: 'M8 8h8' }],
  ['path', { d: 'M8 12h8' }],
  ['path', { d: 'M8 16h8' }],
];

const Azure: LucideIconData = [
  ['path', { d: 'M6 21L13.5 3H17l-4 8h5L7 21h-1z' }],
];

const Tavily: LucideIconData = [
  ['circle', { cx: '11', cy: '11', r: '8' }],
  ['path', { d: 'm21 21-4.3-4.3' }],
  ['path', { d: 'M11 8v6' }],
  ['path', { d: 'M8 11h6' }],
];

const BalanceScale: LucideIconData = [
  ['path', { d: 'M12 3v19' }],
  ['path', { d: 'M5 8h14' }],
  ['path', { d: 'M3 16l2-8 2 8a4.5 4.5 0 0 1-4 0z' }],
  ['path', { d: 'M17 16l2-8 2 8a4.5 4.5 0 0 1-4 0z' }],
  ['circle', { cx: '12', cy: '3', r: '1' }],
];

const Postgres: LucideIconData = [
  ['ellipse', { cx: '12', cy: '5', rx: '9', ry: '3' }],
  ['path', { d: 'M3 5v6c0 1.66 4.03 3 9 3s9-1.34 9-3V5' }],
  ['path', { d: 'M3 11v6c0 1.66 4.03 3 9 3s9-1.34 9-3v-6' }],
];

const Mysql: LucideIconData = [
  ['ellipse', { cx: '12', cy: '5', rx: '9', ry: '3' }],
  ['path', { d: 'M3 5v14c0 1.66 4.03 3 9 3s9-1.34 9-3V5' }],
  ['path', { d: 'M3 12c0 1.66 4.03 3 9 3s9-1.34 9-3' }],
];

const Oracle: LucideIconData = [
  ['ellipse', { cx: '12', cy: '12', rx: '10', ry: '8' }],
  ['ellipse', { cx: '12', cy: '12', rx: '6', ry: '4' }],
];

const Mongo: LucideIconData = [
  ['path', { d: 'M12 2C10 4 7 7 7 12c0 3.5 2.5 6 5 8' }],
  ['path', { d: 'M12 2c2 2 5 5 5 10 0 3.5-2.5 6-5 8' }],
  ['path', { d: 'M12 22v-2' }],
  ['line', { x1: '12', y1: '10', x2: '12', y2: '14' }],
];

const Redis: LucideIconData = [
  ['path', { d: 'M12 2L2 7l10 5 10-5-10-5z' }],
  ['path', { d: 'M2 17l10 5 10-5' }],
  ['path', { d: 'M2 12l10 5 10-5' }],
];

const Neo4j: LucideIconData = [
  ['circle', { cx: '12', cy: '6', r: '3' }],
  ['circle', { cx: '6', cy: '18', r: '3' }],
  ['circle', { cx: '18', cy: '18', r: '3' }],
  ['line', { x1: '12', y1: '9', x2: '6', y2: '15' }],
  ['line', { x1: '12', y1: '9', x2: '18', y2: '15' }],
  ['line', { x1: '6', y1: '18', x2: '18', y2: '18' }],
];

const Mcp: LucideIconData = [
  ['path', { d: 'M4 6a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6z' }],
  ['path', { d: 'M13 15a2 2 0 0 1 2-2h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3z' }],
  ['path', { d: 'M9 8h3a3 3 0 0 1 3 3v3' }],
  ['path', { d: 'M15 11v3' }],
];

const Datadog: LucideIconData = [
  ['circle', { cx: '12', cy: '12', r: '10' }],
  ['circle', { cx: '9', cy: '10', r: '1.5' }],
  ['circle', { cx: '15', cy: '10', r: '1.5' }],
  ['path', { d: 'M8 15c1.5 2 6.5 2 8 0' }],
];

const Signpost: LucideIconData = [
  ['path', { d: 'M12 3v18' }],
  ['path', { d: 'M12 5h7l2 2-2 2h-7z' }],
  ['path', { d: 'M12 12H5l-2 2 2 2h7z' }],
];

// ---------------------------------------------------------------------------
// All icons used as node icons across the application.
// Includes standard Lucide icons, custom brand icons, and brand aliases.
// Add new node icons here — they'll be available in the palette,
// parameter panel, and anywhere else that uses NODE_ICON_SET.
//
// Keys are PascalCase. lucide-angular normalizes them to kebab-case for
// lookup, so `GoogleSheets: Table2` is found when the template requests
// the name "googleSheets".
// ---------------------------------------------------------------------------
export const NODE_ICON_SET = {
  // =======================================================================
  // Standard Lucide icons (direct references — backend name matches icon)
  // =======================================================================
  ArrowRight,
  ArrowUpNarrowWide,
  Bell,
  Bolt,
  Book,
  BookOpen,
  Bot,
  Brain,
  Calculator,
  CalendarClock,
  ChartBar,
  ChartLine,
  ClipboardList,
  Clock,
  CloudSun,
  Code,
  Cog,
  CopyMinus,
  Database,
  Eye,
  File,
  FileArchive,
  FileCode,
  FileInput,
  FileText,
  GitCompare,
  Globe,
  HardDriveUpload,
  Hash,
  Inbox,
  Key,
  Layers,
  Lightbulb,
  Link,
  ListEnd,
  ListFilter,
  Lock,
  Merge,
  Newspaper,
  Palette,
  Pen,
  Play,
  Repeat,
  Replace,
  Rocket,
  Route,
  Rss,
  ScanText,
  Scissors,
  Search,
  Sigma,
  Split,
  Table2,
  Tag,
  Terminal,
  Timer,
  Triangle,
  Type,
  UnfoldVertical,
  Umbrella,
  Webhook,

  // New node icons
  Bug,
  FileJson,
  Gauge,
  Radar,
  RotateCw,
  StickyNote,

  // =======================================================================
  // Custom brand icons (SVG definitions above)
  // =======================================================================
  Openai,
  Anthropic,
  Google,
  Ollama,
  Mistral,
  Azure,
  Tavily,
  Mcp,
  BalanceScale,
  Datadog,
  Signpost,
  Postgres,
  Mysql,
  Oracle,
  Mongo,
  Redis,
  Neo4j,

  // =======================================================================
  // Aliased Lucide icons — backend name differs from Lucide icon name
  // =======================================================================
  AddressCard: IdCard,
  Comment: MessageCircle,
  Comments: MessageCircle,
  Email: Mail,
  EmailSend: Send,
  Envelope: Mail,
  AlertTriangle: TriangleAlert,
  ErrorTrigger: TriangleAlert,
  ExclamationTriangle: TriangleAlert,
  EditImage: Image,
  FileExport: FileOutput,
  FileImport: FileInput,
  Flow: Workflow,
  Git: GitBranch,
  Language: Languages,
  LocalFileTrigger: File,
  Robot: Bot,
  SpreadsheetFile: FileSpreadsheet,
  SseTrigger: Radio,
  WandMagicSparkles: WandSparkles,

  // =======================================================================
  // CRM / Users  (→ Users)
  // =======================================================================
  Affinity: Users,
  AgileCrm: Users,
  BambooHr: Users,
  Clearbit: Users,
  Copper: Users,
  Dropcontact: Contact,
  FreshworksCrm: Users,
  HighLevel: Users,
  Hubspot: Users,
  HumanticAi: Users,
  Hunter: Users,
  Keap: Users,
  LoneScale: Users,
  MonicaCrm: Users,
  Pipedrive: Users,
  Salesforce: Users,
  Salesmate: Users,
  ZohoCrm: Users,
  Ldap: Users,

  // =======================================================================
  // Communication / Chat  (→ MessageSquare)
  // =======================================================================
  CiscoWebex: MessageSquare,
  Discord: MessageSquare,
  Drift: MessageSquare,
  Intercom: MessageSquare,
  Line: MessageSquare,
  Mattermost: MessageSquare,
  Matrix: MessageSquare,
  MessageBird: MessageSquare,
  Rocketchat: MessageSquare,
  Slack: MessageSquare,
  Twake: MessageSquare,
  Twist: MessageSquare,
  WhatsApp: MessageSquare,
  Zulip: MessageSquare,
  Discourse: MessageSquare,

  // =======================================================================
  // Email / Marketing  (→ Mail)
  // =======================================================================
  ActiveCampaign: Mail,
  Brevo: Send,
  ConvertKit: Mail,
  CustomerIo: Mail,
  Egoi: Mail,
  Emelia: Mail,
  GetResponse: Mail,
  Gmail: Mail,
  Iterable: Mail,
  Lemlist: Mail,
  Mailchimp: Mail,
  MailerLite: Mail,
  Mailgun: Mail,
  Mailjet: Mail,
  Mandrill: Mail,
  Postmark: Mail,
  SendGrid: Send,
  Vero: Mail,

  // =======================================================================
  // Phone / Telephony  (→ Phone)
  // =======================================================================
  Gong: Phone,
  Plivo: Phone,
  Twilio: Phone,
  Vonage: Phone,

  // =======================================================================
  // Cloud  (→ Cloud)
  // =======================================================================
  Aws: Cloud,
  Cloudflare: Cloud,
  Netlify: Cloud,
  Nextcloud: Cloud,

  // =======================================================================
  // Database  (→ Database)
  // =======================================================================
  AwsDynamoDb: Database,
  AzureCosmosDb: Database,
  Cratedb: Database,
  Elasticsearch: Database,
  GoogleBigQuery: Database,
  GoogleFirebaseCloudFirestore: Database,
  GoogleFirebaseRealtimeDatabase: Database,
  MicrosoftSql: Database,
  NocoDb: Database,
  Questdb: Database,
  QuickBase: Database,
  Snowflake: Database,
  Supabase: Database,
  Timescaledb: Database,

  // =======================================================================
  // Storage  (→ HardDrive)
  // =======================================================================
  AwsS3: HardDrive,
  AzureStorage: HardDrive,
  Box: HardDrive,
  Dropbox: HardDrive,
  GoogleDrive: HardDrive,
  MicrosoftOneDrive: HardDrive,
  S3: HardDrive,

  // =======================================================================
  // Security / Shield  (→ Shield)
  // =======================================================================
  AwsIam: Shield,
  Bitwarden: Shield,
  ElasticSecurity: Shield,
  MicrosoftEntra: Shield,
  MicrosoftGraphSecurity: Shield,
  Misp: Shield,
  SecurityScorecard: Shield,
  TheHive: Shield,
  Venafi: Shield,
  UrlScanIo: Shield,

  // =======================================================================
  // Auth / Key  (→ Key)
  // =======================================================================
  AwsCognito: Key,
  Okta: Key,

  // =======================================================================
  // Notification / Bell  (→ Bell)
  // =======================================================================
  AwsSns: Bell,
  PagerDuty: Bell,
  Pushbullet: Bell,
  Pushcut: Bell,
  Signl4: Bell,

  // =======================================================================
  // DevOps / Git  (→ GitBranch)
  // =======================================================================
  Bitbucket: GitBranch,
  Github: GitBranch,
  Gitlab: GitBranch,

  // =======================================================================
  // CI/CD  (→ RefreshCw)
  // =======================================================================
  CircleCi: RefreshCw,
  TravisCi: RefreshCw,

  // =======================================================================
  // Support / Helpdesk  (→ LifeBuoy)
  // =======================================================================
  Freshdesk: LifeBuoy,
  Freshservice: LifeBuoy,
  HaloPsa: LifeBuoy,
  HelpScout: LifeBuoy,
  Servicenow: LifeBuoy,
  Zammad: LifeBuoy,
  Zendesk: LifeBuoy,

  // =======================================================================
  // Forms / Survey  (→ ClipboardList)
  // =======================================================================
  FormIo: ClipboardList,
  Formstack: ClipboardList,
  JotForm: ClipboardList,
  KoBoToolbox: ClipboardList,
  SurveyMonkey: ClipboardList,
  Typeform: ClipboardList,
  Wufoo: ClipboardList,

  // =======================================================================
  // Task / Check  (→ SquareCheck)
  // =======================================================================
  Asana: SquareCheck,
  ClickUp: SquareCheck,
  GoogleTasks: SquareCheck,
  MicrosoftToDo: SquareCheck,
  Todoist: SquareCheck,

  // =======================================================================
  // Payment  (→ CreditCard)
  // =======================================================================
  Chargebee: CreditCard,
  Paddle: CreditCard,
  PayPal: CreditCard,
  Stripe: CreditCard,
  Wise: CreditCard,

  // =======================================================================
  // E-commerce  (→ ShoppingCart)
  // =======================================================================
  Gumroad: ShoppingCart,
  Magento: ShoppingCart,
  WooCommerce: ShoppingCart,

  // =======================================================================
  // Shopify  (→ ShoppingBag)
  // =======================================================================
  Shopify: ShoppingBag,

  // =======================================================================
  // Finance  (→ DollarSign)
  // =======================================================================
  CoinGecko: Coins,
  InvoiceNinja: DollarSign,
  ProfitWell: DollarSign,
  QuickBooks: DollarSign,
  Xero: DollarSign,

  // =======================================================================
  // CMS / Layout  (→ LayoutDashboard)
  // =======================================================================
  Adalo: LayoutDashboard,
  Baserow: LayoutDashboard,
  Bubble: LayoutDashboard,
  Cockpit: LayoutDashboard,
  Coda: LayoutDashboard,
  Contentful: LayoutDashboard,
  Storyblok: LayoutDashboard,
  Strapi: LayoutDashboard,
  Webflow: LayoutDashboard,
  Wordpress: LayoutDashboard,
  MondayCom: LayoutDashboard,

  // =======================================================================
  // Kanban / Grid  (→ LayoutGrid)
  // =======================================================================
  Trello: LayoutGrid,
  Wekan: LayoutGrid,

  // =======================================================================
  // Table  (→ Table2)
  // =======================================================================
  Airtable: Table2,
  Grist: Table2,
  MicrosoftExcel: Table2,
  GoogleSheets: Table2,
  SeaTable: Table2,
  Stackby: Table2,

  // =======================================================================
  // Google services
  // =======================================================================
  GoogleAds: Megaphone,
  GoogleAnalytics: ChartBar,
  GoogleBooks: BookOpen,
  GoogleBusinessProfile: Building,
  GoogleCalendar: Calendar,
  GoogleChat: MessageSquare,
  GoogleCloudNaturalLanguage: Languages,
  GoogleCloudStorage: Cloud,
  GoogleContacts: Users,
  GoogleDocs: FileText,
  GoogleGemini: Sparkles,
  GooglePerspective: Eye,
  GoogleSlides: FileText,
  GoogleWorkspaceAdmin: Settings,

  // =======================================================================
  // Microsoft services
  // =======================================================================
  Microsoft: Monitor,
  MicrosoftDynamicsCrm: Users,
  MicrosoftOutlook: Mail,
  MicrosoftSharePoint: Share2,
  MicrosoftTeams: MessageSquare,

  // =======================================================================
  // Calendar / Scheduling  (→ Calendar)
  // =======================================================================
  AcuityScheduling: Calendar,
  Cal: Calendar,
  Calendly: Calendar,
  Eventbrite: Calendar,

  // =======================================================================
  // Time tracking  (→ Clock)
  // =======================================================================
  Clockify: Clock,
  Harvest: Clock,
  Toggl: Clock,

  // =======================================================================
  // Marketing  (→ Megaphone)
  // =======================================================================
  ActionNetwork: Megaphone,
  Autopilot: Megaphone,
  Mautic: Megaphone,
  Tapfiliate: Share2,

  // =======================================================================
  // Analytics  (→ ChartBar)
  // =======================================================================
  Grafana: ChartBar,
  Metabase: ChartBar,
  PostHog: ChartBar,
  Segment: ChartBar,
  Splunk: ChartBar,

  // =======================================================================
  // Video  (→ Video)
  // =======================================================================
  Demio: Video,
  GoToWebinar: Video,
  YouTube: Video,
  Zoom: Video,

  // =======================================================================
  // Social  (→ Share2)
  // =======================================================================
  Facebook: Share2,
  LinkedIn: Share2,

  // Twitter  (→ AtSign)
  Twitter: AtSign,

  // Reddit  (→ MessageCircle)
  Reddit: MessageCircle,

  // =======================================================================
  // AI / ML  (→ Brain)
  // =======================================================================
  Cortex: Brain,
  Huggingface: Bot,
  JinaAi: Brain,
  Perplexity: Brain,
  Phantombuster: Bot,

  // =======================================================================
  // Misc — individual mappings
  // =======================================================================
  Airtop: Globe,
  Amqp: ArrowLeftRight,
  ApiTemplateIo: FileImage,
  Bannerbear: Image,
  Beeminder: Target,
  DeepL: Languages,
  Dhl: Truck,
  ErpNext: Building,
  Figma: PenTool,
  FileMaker: File,
  Graphql: Code,
  HomeAssistant: House,
  Kafka: ArrowLeftRight,
  Linear: ClipboardList,
  Medium: FileText,
  Mindee: ScanText,
  Mqtt: Wifi,
  Netscaler: Globe,
  Notion: FileText,
  Npm: Package,
  Odoo: Building,
  Onfleet: Truck,
  Oura: Activity,
  PhilipsHue: Lightbulb,
  RabbitMq: ArrowLeftRight,
  Raindrop: Bookmark,
  Rundeck: Terminal,
  SentryIo: Bug,
  Spotify: Music,
  Ssh: Terminal,
  Strava: Activity,
  SyncroMsp: Settings,
  Telegram: Send,
  UnleashedSoftware: Package,
  UptimeRobot: Activity,
  Workable: Briefcase,
  Ghost: FileText,

  // =======================================================================
  // Utility — extra standard Lucide icons used elsewhere
  // =======================================================================
  Reply,
  ShieldCheck,
};
