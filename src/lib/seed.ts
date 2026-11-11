/**
 * DEMO SEED DATA – every record here is sample content for the prototype only.
 * Prices, people, schedules and results are NOT real academy information.
 */
import type * as T from "./types";
import { MODULES } from "./types";
import { addMonths, dayOffset, seeded } from "./utils";

const allAccess = (a: T.Access): T.Permissions => Object.fromEntries(MODULES.map((m) => [m, a])) as T.Permissions;

export const PERMISSION_PRESETS: Record<"owner" | "staff" | "faculty", T.Permissions> = {
  owner: allAccess("manage"),
  staff: {
    ...allAccess("none"),
    enquiries: "manage", students: "manage", admissions: "manage", fees: "manage", library: "manage",
    notifications: "manage", support: "manage", courses: "view", batches: "view", attendance: "view",
    reports: "view", examUpdates: "manage", certificates: "manage", results: "view",
  },
  faculty: {
    ...allAccess("none"),
    students: "view", attendance: "manage", materials: "manage", classes: "manage",
    questionBank: "view", exams: "view", assignments: "manage", results: "none",
  },
};

const branches: T.Branch[] = [
  { id: "br_main", name: { en: "Main Branch", mr: "मुख्य शाखा" }, city: "Maharashtra", active: true },
];
const classrooms: T.Classroom[] = [
  { id: "cr_1", branchId: "br_main", name: "Classroom 1 (Hall A)", capacity: 60 },
  { id: "cr_2", branchId: "br_main", name: "Classroom 2", capacity: 40 },
  { id: "cr_3", branchId: "br_main", name: "Classroom 3 (Seminar)", capacity: 30 },
];

const L = (en: string, mr: string): T.L => ({ en, mr });

export const DEFAULT_HOME_SECTIONS: T.HomeSection[] = (["results", "director", "vision", "courses", "explore", "why", "stats", "batches", "reviews", "library", "cta"] as T.HomeSectionKey[]).map((key) => ({ key, enabled: true }));

export const DEFAULT_DIRECTOR: T.DirectorProfile = {
  name: L("Nikhil Rudrawar", "निखिल रुद्रवार"),
  title: L("Founder & Director", "संस्थापक व संचालक"),
  bio: L("", ""),
  highlights: [],
};
/** Numbers stay empty and hidden until the academy enters its real figures. */
export const DEFAULT_STATS: T.HomeStat[] = [
  { id: "st_guided", value: "", label: L("Students guided", "मार्गदर्शन केलेले विद्यार्थी"), enabled: true },
  { id: "st_selected", value: "", label: L("Successful students", "यशस्वी विद्यार्थी"), enabled: true },
  { id: "st_years", value: "", label: L("Years of experience", "अनुभवाची वर्षे"), enabled: true },
  { id: "st_batch", value: "", label: L("Students per batch", "प्रति बॅच विद्यार्थी"), enabled: true },
];

const SAMPLE_EXAMS: [string, string][] = [
  ["MPSC Rajyaseva", "एमपीएससी राज्यसेवा"], ["PSI", "पीएसआय"], ["STI", "एसटीआय"], ["ASO", "एएसओ"], ["Police Bharti", "पोलीस भरती"],
  ["Talathi Bharti", "तलाठी भरती"], ["Combined Group B", "संयुक्त गट ब"], ["Combined Group C", "संयुक्त गट क"], ["MPSC Rajyaseva", "एमपीएससी राज्यसेवा"], ["PSI", "पीएसआय"],
];
const MR_DIGITS = "०१२३४५६७८९";
const mrNum = (n: number) => String(n).replace(/\d/g, (d) => MR_DIGITS[Number(d)]);

/** Placeholder result cards, clearly marked as samples, until the academy adds real results. */
export const SAMPLE_ACHIEVEMENTS: T.Achievement[] = SAMPLE_EXAMS.map(([en, mr], i) => ({
  id: `ac_sample_${String(i + 1).padStart(2, "0")}`,
  name: L(`Sample student ${i + 1}`, `नमुना विद्यार्थी ${mrNum(i + 1)}`),
  exam: L(en, mr),
  result: L("Sample – replace with real result", "नमुना – खऱ्या निकालाने बदला"),
  photo: `/results/sample-${String(i + 1).padStart(2, "0")}.svg`,
  consent: true,
  consentNote: "Sample placeholder, not a real student",
  published: true,
  demo: true,
}));

/** Layouts saved by the version with the photo strip used the old default order, so they fall back to the new default. */
export function resolveHomeSections(saved?: T.HomeSection[]): T.HomeSection[] {
  const known = new Set(DEFAULT_HOME_SECTIONS.map((s) => s.key));
  if (!saved || saved.some((s) => !known.has(s.key))) return DEFAULT_HOME_SECTIONS;
  const kept = saved;
  const missing = DEFAULT_HOME_SECTIONS.filter((d) => !kept.some((s) => s.key === d.key));
  return [...kept, ...missing];
}

const courses: T.Course[] = [
  {
    id: "c_rajyaseva", slug: "mpsc-rajyaseva", category: "state",
    name: L("MPSC Rajyaseva", "एमपीएससी राज्यसेवा"),
    short: L("Complete Prelims + Mains preparation for State Services.", "राज्यसेवा पूर्व + मुख्य परीक्षेची संपूर्ण तयारी."),
    description: L(
      "Structured coaching for the Maharashtra State Services examination covering General Studies, CSAT, essay practice and regular mock tests with detailed analysis.",
      "महाराष्ट्र राज्यसेवा परीक्षेसाठी सामान्य अध्ययन, सीसॅट, निबंध सराव आणि सविस्तर विश्लेषणासह नियमित सराव चाचण्या असलेले संरचित मार्गदर्शन."
    ),
    durationMonths: 12, mediums: ["mr", "en"], fee: 45000, admissionOpen: true, published: true, color: "from-brand-700 to-brand-500",
    seo: { title: "MPSC Rajyaseva Coaching | Vidyadhan Academy", description: "MPSC Rajyaseva prelims and mains coaching in Marathi and English medium." },
    documentsRequired: [], syllabusVersion: 2,
  },
  {
    id: "c_psi", slug: "psi-sti-aso", category: "combined",
    name: L("PSI / STI / ASO", "पीएसआय / एसटीआय / एएसओ"),
    short: L("Combined Group B preparation for PSI, STI and ASO posts.", "पीएसआय, एसटीआय व एएसओ पदांसाठी तयारी."),
    description: L(
      "Focused preparation for Police Sub-Inspector, State Tax Inspector and Assistant Section Officer examinations, with special emphasis on objective practice.",
      "पोलीस उपनिरीक्षक, राज्य कर निरीक्षक व सहायक कक्ष अधिकारी परीक्षांसाठी वस्तुनिष्ठ सरावावर भर देणारी तयारी."
    ),
    durationMonths: 8, mediums: ["mr"], fee: 32000, admissionOpen: true, published: true, color: "from-sky-700 to-sky-500",
    seo: { title: "PSI STI ASO Coaching | Vidyadhan Academy", description: "Coaching for PSI, STI and ASO examinations." },
    documentsRequired: [], syllabusVersion: 1,
  },
  {
    id: "c_police", slug: "police-bharti", category: "police",
    name: L("Police Bharti", "पोलीस भरती"),
    short: L("Written exam preparation with physical test guidance.", "लेखी परीक्षा तयारी व शारीरिक चाचणी मार्गदर्शन."),
    description: L(
      "Preparation for Maharashtra Police Constable recruitment covering arithmetic, reasoning, Marathi grammar, general knowledge and guidance for the physical test.",
      "महाराष्ट्र पोलीस शिपाई भरतीसाठी अंकगणित, बुद्धिमत्ता, मराठी व्याकरण, सामान्य ज्ञान आणि शारीरिक चाचणी मार्गदर्शन."
    ),
    durationMonths: 4, mediums: ["mr"], fee: 15000, admissionOpen: true, published: true, color: "from-emerald-700 to-emerald-500",
    seo: { title: "Police Bharti Coaching | Vidyadhan Academy", description: "Maharashtra Police Bharti written exam coaching." },
    documentsRequired: [], syllabusVersion: 1,
  },
  {
    id: "c_talathi", slug: "talathi-bharti", category: "revenue",
    name: L("Talathi Bharti", "तलाठी भरती"),
    short: L("Revenue department recruitment preparation.", "महसूल विभाग भरतीची तयारी."),
    description: L(
      "Complete syllabus coverage for Talathi recruitment including Marathi, English, general knowledge, and intelligence tests with daily practice.",
      "तलाठी भरतीसाठी मराठी, इंग्रजी, सामान्य ज्ञान आणि बुद्धिमत्ता चाचणी यांचा दैनंदिन सरावासह संपूर्ण अभ्यासक्रम."
    ),
    durationMonths: 4, mediums: ["mr"], fee: 14000, admissionOpen: false, published: true, color: "from-amber-700 to-amber-500",
    seo: { title: "Talathi Bharti Coaching | Vidyadhan Academy", description: "Talathi recruitment exam coaching." },
    documentsRequired: [], syllabusVersion: 1,
  },
  {
    id: "c_groupb", slug: "combined-group-b", category: "combined",
    name: L("Combined Group B", "संयुक्त गट ब"),
    short: L("MPSC Combined Group B (non-gazetted) preparation.", "एमपीएससी संयुक्त गट ब (अराजपत्रित) तयारी."),
    description: L(
      "Coaching for the MPSC Combined Group B preliminary and main examinations.",
      "एमपीएससी संयुक्त गट ब पूर्व व मुख्य परीक्षेसाठी मार्गदर्शन."
    ),
    durationMonths: 8, mediums: ["mr", "en"], fee: 30000, admissionOpen: true, published: true, color: "from-violet-700 to-violet-500",
    seo: { title: "MPSC Combined Group B | Vidyadhan Academy", description: "MPSC Combined Group B coaching." },
    documentsRequired: [], syllabusVersion: 1,
  },
  {
    id: "c_groupc", slug: "combined-group-c", category: "combined",
    name: L("Combined Group C", "संयुक्त गट क"),
    short: L("MPSC Combined Group C preparation.", "एमपीएससी संयुक्त गट क तयारी."),
    description: L("Coaching for the MPSC Combined Group C examinations.", "एमपीएससी संयुक्त गट क परीक्षांसाठी मार्गदर्शन."),
    durationMonths: 6, mediums: ["mr"], fee: 22000, admissionOpen: true, published: true, color: "from-rose-700 to-rose-500",
    seo: { title: "MPSC Combined Group C | Vidyadhan Academy", description: "MPSC Combined Group C coaching." },
    documentsRequired: [], syllabusVersion: 1,
  },
  {
    id: "c_other", slug: "other-recruitment", category: "other",
    name: L("Other Recruitment Exams", "इतर भरती परीक्षा"),
    short: L("Short programmes for upcoming recruitment notifications.", "आगामी भरती जाहिरातींसाठी अल्पकालीन कार्यक्रम."),
    description: L(
      "Short-term crash courses launched as new recruitment notifications are announced (e.g. ZP, Forest, Gram Sevak). Contact the office for the current list.",
      "नवीन भरती जाहिराती प्रसिद्ध झाल्यावर सुरू होणारे अल्पकालीन क्रॅश कोर्स (उदा. जि.प., वन विभाग, ग्रामसेवक). सद्य यादीसाठी कार्यालयाशी संपर्क साधा."
    ),
    durationMonths: 2, mediums: ["mr"], fee: 6000, admissionOpen: true, published: true, color: "from-slate-700 to-slate-500",
    seo: { title: "Recruitment Exam Crash Courses | Vidyadhan Academy", description: "Crash courses for Maharashtra recruitment exams." },
    documentsRequired: [], syllabusVersion: 1,
  },
];

const ch = (id: string, en: string, mr: string, topics: [string, string][]): T.Chapter => ({ id, name: L(en, mr), topics: topics.map(([a, b]) => L(a, b)) });

const subjects: T.Subject[] = [
  { id: "s_hist", courseId: "c_rajyaseva", name: L("History", "इतिहास"), chapters: [
    ch("ch_h1", "Ancient India", "प्राचीन भारत", [["Indus Valley Civilisation", "सिंधू संस्कृती"], ["Mauryan Empire", "मौर्य साम्राज्य"]]),
    ch("ch_h2", "Modern India", "आधुनिक भारत", [["Battle of Plassey", "प्लासीची लढाई"], ["1857 Revolt", "१८५७ चा उठाव"]]),
    ch("ch_h3", "Social Reformers of Maharashtra", "महाराष्ट्रातील समाजसुधारक", [["Mahatma Phule", "महात्मा फुले"], ["Shahu Maharaj", "शाहू महाराज"]]),
  ]},
  { id: "s_geo", courseId: "c_rajyaseva", name: L("Geography", "भूगोल"), chapters: [
    ch("ch_g1", "Physical Geography of Maharashtra", "महाराष्ट्राचा प्राकृतिक भूगोल", [["Rivers", "नद्या"], ["Mountain ranges", "पर्वतरांगा"]]),
    ch("ch_g2", "Indian Geography", "भारताचा भूगोल", [["Monsoon", "मान्सून"], ["Soils", "मृदा"]]),
  ]},
  { id: "s_polity", courseId: "c_rajyaseva", name: L("Polity", "राज्यव्यवस्था"), chapters: [
    ch("ch_p1", "Constitution", "राज्यघटना", [["Fundamental Rights", "मूलभूत हक्क"], ["Fundamental Duties", "मूलभूत कर्तव्ये"]]),
    ch("ch_p2", "State Government", "राज्य शासन", [["Governor", "राज्यपाल"], ["State Legislature", "राज्य विधिमंडळ"]]),
  ]},
  { id: "s_eco", courseId: "c_rajyaseva", name: L("Economics", "अर्थशास्त्र"), chapters: [
    ch("ch_e1", "Banking & Monetary Policy", "बँकिंग व चलनविषयक धोरण", [["RBI", "रिझर्व्ह बँक"], ["Repo rate", "रेपो दर"]]),
  ]},
  { id: "s_sci", courseId: "c_rajyaseva", name: L("General Science", "सामान्य विज्ञान"), chapters: [
    ch("ch_s1", "Physics basics", "भौतिकशास्त्र मूलतत्त्वे", [["Units", "एकके"], ["Motion", "गती"]]),
    ch("ch_s2", "Biology", "जीवशास्त्र", [["Vitamins", "जीवनसत्त्वे"]]),
  ]},
  { id: "s_csat", courseId: "c_rajyaseva", name: L("CSAT / Reasoning", "सीसॅट / बुद्धिमत्ता"), chapters: [
    ch("ch_r1", "Series & Coding", "मालिका व सांकेतिक भाषा", [["Number series", "संख्या मालिका"], ["Coding-decoding", "सांकेतिक भाषा"]]),
  ]},
  { id: "s_pol_math", courseId: "c_police", name: L("Arithmetic", "अंकगणित"), chapters: [
    ch("ch_pm1", "Percentages", "टक्केवारी", [["Basics", "मूलभूत"]]),
  ]},
  { id: "s_pol_mar", courseId: "c_police", name: L("Marathi Grammar", "मराठी व्याकरण"), chapters: [
    ch("ch_pmr1", "Synonyms & Antonyms", "समानार्थी व विरुद्धार्थी शब्द", [["Synonyms", "समानार्थी"]]),
  ]},
  { id: "s_pol_gk", courseId: "c_police", name: L("General Knowledge", "सामान्य ज्ञान"), chapters: [
    ch("ch_pg1", "Maharashtra GK", "महाराष्ट्र सामान्य ज्ञान", [["Districts", "जिल्हे"]]),
  ]},
  { id: "s_psi_gs", courseId: "c_psi", name: L("General Studies", "सामान्य अध्ययन"), chapters: [
    ch("ch_pgs1", "Polity & History", "राज्यव्यवस्था व इतिहास", [["Mixed", "मिश्र"]]),
  ]},
  { id: "s_psi_eng", courseId: "c_psi", name: L("English", "इंग्रजी"), chapters: [
    ch("ch_pe1", "Vocabulary", "शब्दसंग्रह", [["Antonyms", "विरुद्धार्थी"]]),
  ]},
  { id: "s_tal_gk", courseId: "c_talathi", name: L("General Knowledge", "सामान्य ज्ञान"), chapters: [ch("ch_tg1", "Maharashtra", "महाराष्ट्र", [["Basics", "मूलभूत"]])] },
  { id: "s_gb_gs", courseId: "c_groupb", name: L("General Studies", "सामान्य अध्ययन"), chapters: [ch("ch_gb1", "Mixed", "मिश्र", [["Mixed", "मिश्र"]])] },
  { id: "s_gc_gs", courseId: "c_groupc", name: L("General Studies", "सामान्य अध्ययन"), chapters: [ch("ch_gc1", "Mixed", "मिश्र", [["Mixed", "मिश्र"]])] },
];

const P = PERMISSION_PRESETS;
const staff: T.StaffUser[] = [
  { id: "u_owner", name: "Owner (Demo)", mobile: "9000000001", role: "owner", designation: "Director", subjects: [], batchIds: [], permissions: P.owner, publishWithoutApproval: true, active: true },
  { id: "u_office", name: "Office Staff (Demo)", mobile: "9000000002", role: "staff", designation: "Admissions & Accounts", subjects: [], batchIds: [], permissions: P.staff, publishWithoutApproval: false, active: true },
  { id: "u_fac1", name: "Faculty – History (Demo)", mobile: "9000000003", role: "faculty", designation: "Faculty", subjects: ["s_hist", "s_polity"], batchIds: ["b_rs_m", "b_rs_e"], permissions: P.faculty, publishWithoutApproval: false, active: true },
  { id: "u_fac2", name: "Faculty – Reasoning (Demo)", mobile: "9000000004", role: "faculty", designation: "Faculty", subjects: ["s_csat", "s_pol_math"], batchIds: ["b_rs_m", "b_pol"], permissions: { ...P.faculty, results: "view", questionBank: "manage" }, publishWithoutApproval: true, active: true },
  { id: "u_fac3", name: "Faculty – Marathi & GK (Demo)", mobile: "9000000005", role: "faculty", designation: "Faculty", subjects: ["s_pol_mar", "s_pol_gk", "s_geo"], batchIds: ["b_pol", "b_psi"], permissions: P.faculty, publishWithoutApproval: false, active: true },
];

const batches: T.Batch[] = [
  { id: "b_rs_m", courseId: "c_rajyaseva", branchId: "br_main", name: "Rajyaseva Morning (Offline)", mode: "offline", startDate: dayOffset(-60), endDate: dayOffset(305), days: "Mon–Sat", time: "07:30 – 10:30", capacity: 60, facultyIds: ["u_fac1", "u_fac2"], classroomId: "cr_1", fee: 45000, admissionOpen: true, admissionCloses: dayOffset(15) },
  { id: "b_rs_e", courseId: "c_rajyaseva", branchId: "br_main", name: "Rajyaseva Evening (Hybrid)", mode: "hybrid", startDate: dayOffset(-30), endDate: dayOffset(335), days: "Mon–Fri", time: "17:30 – 20:00", capacity: 40, facultyIds: ["u_fac1"], classroomId: "cr_2", onlineLink: "https://meet.google.com/demo-link", fee: 42000, admissionOpen: true, admissionCloses: dayOffset(20) },
  { id: "b_psi", courseId: "c_psi", branchId: "br_main", name: "PSI/STI/ASO Day Batch", mode: "offline", startDate: dayOffset(-20), endDate: dayOffset(220), days: "Mon–Sat", time: "11:00 – 14:00", capacity: 50, facultyIds: ["u_fac3"], classroomId: "cr_2", fee: 32000, admissionOpen: true, admissionCloses: dayOffset(10) },
  { id: "b_pol", courseId: "c_police", branchId: "br_main", name: "Police Bharti Batch 7", mode: "offline", startDate: dayOffset(-15), endDate: dayOffset(105), days: "Mon–Sat", time: "06:30 – 09:00", capacity: 60, facultyIds: ["u_fac2", "u_fac3"], classroomId: "cr_1", fee: 15000, admissionOpen: true, admissionCloses: dayOffset(7) },
  { id: "b_gb_on", courseId: "c_groupb", branchId: "br_main", name: "Group B Online", mode: "online", startDate: dayOffset(12), endDate: dayOffset(252), days: "Mon, Wed, Fri", time: "19:00 – 21:00", capacity: 100, facultyIds: ["u_fac1"], onlineLink: "https://zoom.us/j/demo", fee: 26000, admissionOpen: true, admissionCloses: dayOffset(10) },
  { id: "b_gc", courseId: "c_groupc", branchId: "br_main", name: "Group C Weekend", mode: "offline", startDate: dayOffset(25), endDate: dayOffset(205), days: "Sat–Sun", time: "09:00 – 13:00", capacity: 30, facultyIds: ["u_fac3"], classroomId: "cr_3", fee: 22000, admissionOpen: true, admissionCloses: dayOffset(22) },
];

const FIRST = ["Aarti", "Rohan", "Snehal", "Prathamesh", "Pooja", "Sagar", "Komal", "Akash", "Shubhangi", "Vaibhav", "Rutuja", "Omkar", "Priyanka", "Tushar", "Ashwini", "Nikhil", "Sayali", "Ganesh", "Pallavi", "Sachin", "Mayuri", "Kiran", "Shraddha", "Mahesh"];
const LAST = ["Patil", "Jadhav", "Pawar", "Shinde", "Deshmukh", "Kale", "More", "Gaikwad", "Kulkarni", "Chavan", "Bhosale", "Wagh"];

const authorities: T.Authority[] = [
  { id: "a_mpsc", name: L("MPSC", "एमपीएससी"), group: "Maharashtra", url: "https://mpsc.gov.in" },
  { id: "a_police", name: L("Maharashtra Police", "महाराष्ट्र पोलीस"), group: "Maharashtra", url: "https://www.mahapolice.gov.in" },
  { id: "a_mahagov", name: L("Maharashtra Govt. Recruitment", "महाराष्ट्र शासन भरती"), group: "Maharashtra", url: "https://www.maharashtra.gov.in" },
  { id: "a_upsc", name: L("UPSC", "यूपीएससी"), group: "Central", url: "https://upsc.gov.in" },
  { id: "a_ssc", name: L("SSC", "एसएससी"), group: "Central", url: "https://ssc.gov.in" },
  { id: "a_ibps", name: L("IBPS", "आयबीपीएस"), group: "Banking", url: "https://www.ibps.in" },
  { id: "a_sbi", name: L("SBI", "एसबीआय"), group: "Banking", url: "https://sbi.co.in/web/careers" },
  { id: "a_rbi", name: L("RBI", "आरबीआय"), group: "Banking", url: "https://opportunities.rbi.org.in" },
];

const sources: T.UpdateSource[] = [
  { id: "src_mpsc_x", name: "MPSC on X (@mpsc_office)", kind: "x", url: "https://x.com/mpsc_office", authorityId: "a_mpsc", active: true, integration: "not-configured" },
  { id: "src_mpsc_web", name: "MPSC official website", kind: "website", url: "https://mpsc.gov.in", authorityId: "a_mpsc", active: true, integration: "manual" },
  { id: "src_upsc", name: "UPSC – What's New", kind: "website", url: "https://upsc.gov.in", authorityId: "a_upsc", active: true, integration: "manual" },
  { id: "src_ssc", name: "SSC notices", kind: "website", url: "https://ssc.gov.in", authorityId: "a_ssc", active: true, integration: "manual" },
  { id: "src_ibps", name: "IBPS RSS (configure URL)", kind: "rss", url: "", authorityId: "a_ibps", active: false, integration: "not-configured" },
];

const updates: T.ExamUpdate[] = [
  { id: "eu_1", authorityId: "a_mpsc", sourceId: "src_mpsc_web", title: L("[Demo] MPSC Rajyaseva Prelims – sample notification", "[डेमो] एमपीएससी राज्यसेवा पूर्व – नमुना जाहिरात"), summary: L("Sample entry showing how an approved official update appears. Always verify on the official MPSC website.", "मंजूर अधिकृत सूचना कशी दिसते याचे नमुना उदाहरण. अधिकृत एमपीएससी संकेतस्थळावर नेहमी पडताळणी करा."), sourceUrl: "https://mpsc.gov.in", status: "published", createdAt: dayOffset(-3), urgent: false, dates: [{ kind: "appStart", date: dayOffset(-2) }, { kind: "appEnd", date: dayOffset(6) }, { kind: "exam", date: dayOffset(70) }] },
  { id: "eu_2", authorityId: "a_police", title: L("[Demo] Police Bharti – physical test schedule (sample)", "[डेमो] पोलीस भरती – शारीरिक चाचणी वेळापत्रक (नमुना)"), summary: L("Demo update. Refer to the official Maharashtra Police portal for actual dates.", "डेमो सूचना. प्रत्यक्ष तारखांसाठी अधिकृत महाराष्ट्र पोलीस पोर्टल पहा."), sourceUrl: "https://www.mahapolice.gov.in", status: "published", createdAt: dayOffset(-6), urgent: false, dates: [{ kind: "admitCard", date: dayOffset(3) }, { kind: "exam", date: dayOffset(12) }] },
  { id: "eu_3", authorityId: "a_ssc", title: L("[Demo] SSC CGL – answer key (sample)", "[डेमो] एसएससी सीजीएल – उत्तरतालिका (नमुना)"), summary: L("Demo update for SSC followers.", "एसएससी फॉलो करणाऱ्यांसाठी डेमो सूचना."), sourceUrl: "https://ssc.gov.in", status: "published", createdAt: dayOffset(-10), urgent: false, dates: [{ kind: "answerKey", date: dayOffset(1) }] },
  { id: "eu_4", authorityId: "a_mpsc", sourceId: "src_mpsc_x", title: L("[Fetched – pending review] MPSC post (sample)", "[प्राप्त – पुनरावलोकन बाकी] एमपीएससी पोस्ट (नमुना)"), summary: L("Example of an item collected from a configured source, waiting for admin review.", "कॉन्फिगर केलेल्या स्रोतातून आलेली, प्रशासकीय पुनरावलोकनाची प्रतीक्षा करणारी नोंद."), sourceUrl: "https://x.com/mpsc_office", status: "pending", createdAt: dayOffset(0), urgent: false, dates: [] },
  { id: "eu_5", authorityId: "a_ibps", title: L("[Pending] IBPS PO – sample", "[बाकी] आयबीपीएस पीओ – नमुना"), summary: L("Pending review.", "पुनरावलोकन बाकी."), sourceUrl: "https://www.ibps.in", status: "pending", createdAt: dayOffset(-1), urgent: false, dates: [{ kind: "appEnd", date: dayOffset(14) }] },
  { id: "eu_6", authorityId: "a_mpsc", title: L("Academy notice: Diwali holiday schedule (demo)", "अकॅडमी सूचना: दिवाळी सुट्टी वेळापत्रक (डेमो)"), summary: L("Urgent academy-wide notice example.", "तातडीच्या अकॅडमी-व्यापी सूचनेचे उदाहरण."), sourceUrl: "", status: "published", createdAt: dayOffset(-1), urgent: true, dates: [] },
];

// ---- Question bank: accurate sample GK/reasoning questions (bilingual) ----
type Q4 = [string, string];
const q = (id: string, courseId: string, subjectId: string, chapterId: string, text: Q4, opts: Q4[], answer: number, exp: Q4, difficulty: T.Question["difficulty"] = "easy"): T.Question => ({
  id, courseId, subjectId, chapterId, text: L(...text), options: opts.map((o) => L(...o)), answer, explanation: L(...exp), difficulty, marks: 2, negative: 0.5,
});
const questions: T.Question[] = [
  q("q1", "c_rajyaseva", "s_hist", "ch_h3", ["In which year was the state of Maharashtra formed?", "महाराष्ट्र राज्याची स्थापना कोणत्या वर्षी झाली?"], [["1956", "१९५६"], ["1960", "१९६०"], ["1962", "१९६२"], ["1947", "१९४७"]], 1, ["Maharashtra was formed on 1 May 1960 under the Bombay Reorganisation Act, 1960.", "मुंबई पुनर्रचना कायदा, १९६० अन्वये १ मे १९६० रोजी महाराष्ट्राची स्थापना झाली."]),
  q("q2", "c_rajyaseva", "s_hist", "ch_h3", ["Who founded the Satyashodhak Samaj?", "सत्यशोधक समाजाची स्थापना कोणी केली?"], [["Gopal Ganesh Agarkar", "गोपाळ गणेश आगरकर"], ["Mahatma Jyotirao Phule", "महात्मा ज्योतिराव फुले"], ["Lokmanya Tilak", "लोकमान्य टिळक"], ["Justice Ranade", "न्यायमूर्ती रानडे"]], 1, ["Mahatma Jyotirao Phule founded the Satyashodhak Samaj in 1873.", "महात्मा ज्योतिराव फुले यांनी १८७३ मध्ये सत्यशोधक समाजाची स्थापना केली."]),
  q("q3", "c_rajyaseva", "s_hist", "ch_h2", ["The Battle of Plassey was fought in which year?", "प्लासीची लढाई कोणत्या वर्षी झाली?"], [["1757", "१७५७"], ["1764", "१७६४"], ["1857", "१८५७"], ["1761", "१७६१"]], 0, ["The Battle of Plassey was fought in 1757.", "प्लासीची लढाई १७५७ मध्ये झाली."], "medium"),
  q("q4", "c_rajyaseva", "s_geo", "ch_g1", ["Which is the highest peak in Maharashtra?", "महाराष्ट्रातील सर्वोच्च शिखर कोणते?"], [["Mahabaleshwar", "महाबळेश्वर"], ["Salher", "साल्हेर"], ["Kalsubai", "कळसूबाई"], ["Torna", "तोरणा"]], 2, ["Kalsubai (about 1,646 m) in the Sahyadri range is the highest peak in Maharashtra.", "सह्याद्रीतील कळसूबाई (सुमारे १,६४६ मी.) हे महाराष्ट्रातील सर्वोच्च शिखर आहे."]),
  q("q5", "c_rajyaseva", "s_geo", "ch_g1", ["Which is the longest river flowing through Maharashtra?", "महाराष्ट्रातून वाहणारी सर्वात लांब नदी कोणती?"], [["Krishna", "कृष्णा"], ["Godavari", "गोदावरी"], ["Tapi", "तापी"], ["Bhima", "भीमा"]], 1, ["The Godavari is the longest river in Maharashtra.", "गोदावरी ही महाराष्ट्रातील सर्वात लांब नदी आहे."]),
  q("q6", "c_rajyaseva", "s_polity", "ch_p1", ["Dr. Ambedkar called which Article the 'heart and soul' of the Constitution?", "डॉ. आंबेडकरांनी कोणत्या कलमाला राज्यघटनेचे 'हृदय आणि आत्मा' म्हटले?"], [["Article 14", "कलम १४"], ["Article 21", "कलम २१"], ["Article 32", "कलम ३२"], ["Article 368", "कलम ३६८"]], 2, ["Article 32 (Right to Constitutional Remedies).", "कलम ३२ (घटनात्मक उपायांचा हक्क)."], "medium"),
  q("q7", "c_rajyaseva", "s_polity", "ch_p1", ["Fundamental Duties were added by which Constitutional Amendment?", "मूलभूत कर्तव्ये कोणत्या घटनादुरुस्तीने समाविष्ट करण्यात आली?"], [["42nd", "४२ वी"], ["44th", "४४ वी"], ["52nd", "५२ वी"], ["73rd", "७३ वी"]], 0, ["The 42nd Amendment Act, 1976 added Fundamental Duties (Article 51A).", "४२ वी घटनादुरुस्ती, १९७६ द्वारे मूलभूत कर्तव्ये (कलम ५१अ) समाविष्ट करण्यात आली."], "medium"),
  q("q8", "c_rajyaseva", "s_polity", "ch_p2", ["Who is the constitutional head of a State?", "राज्याचा घटनात्मक प्रमुख कोण असतो?"], [["Chief Minister", "मुख्यमंत्री"], ["Governor", "राज्यपाल"], ["Speaker", "विधानसभा अध्यक्ष"], ["Chief Justice of High Court", "उच्च न्यायालयाचे मुख्य न्यायाधीश"]], 1, ["The Governor is the constitutional head of the State.", "राज्यपाल हे राज्याचे घटनात्मक प्रमुख असतात."]),
  q("q9", "c_rajyaseva", "s_eco", "ch_e1", ["The repo rate in India is decided by:", "भारतातील रेपो दर कोण ठरवते?"], [["Ministry of Finance", "वित्त मंत्रालय"], ["SEBI", "सेबी"], ["Monetary Policy Committee of RBI", "रिझर्व्ह बँकेची चलनविषयक धोरण समिती"], ["NITI Aayog", "नीती आयोग"]], 2, ["The RBI's Monetary Policy Committee sets the policy repo rate.", "रिझर्व्ह बँकेची चलनविषयक धोरण समिती रेपो दर निश्चित करते."], "medium"),
  q("q10", "c_rajyaseva", "s_sci", "ch_s1", ["What is the SI unit of force?", "बलाचे SI एकक कोणते?"], [["Joule", "ज्यूल"], ["Watt", "वॅट"], ["Newton", "न्यूटन"], ["Pascal", "पास्कल"]], 2, ["Force is measured in newtons (N).", "बल न्यूटन (N) मध्ये मोजले जाते."]),
  q("q11", "c_rajyaseva", "s_sci", "ch_s2", ["The chemical name of Vitamin C is:", "जीवनसत्त्व क चे रासायनिक नाव काय आहे?"], [["Ascorbic acid", "अ‍ॅस्कॉर्बिक आम्ल"], ["Retinol", "रेटिनॉल"], ["Calciferol", "कॅल्सिफेरॉल"], ["Thiamine", "थायमिन"]], 0, ["Vitamin C is ascorbic acid.", "जीवनसत्त्व क म्हणजे अ‍ॅस्कॉर्बिक आम्ल."]),
  q("q12", "c_rajyaseva", "s_csat", "ch_r1", ["Find the next number: 2, 6, 12, 20, 30, ?", "पुढील संख्या शोधा: २, ६, १२, २०, ३०, ?"], [["40", "४०"], ["42", "४२"], ["44", "४४"], ["36", "३६"]], 1, ["Differences are 4, 6, 8, 10, so the next difference is 12: 30 + 12 = 42.", "फरक ४, ६, ८, १० असा वाढतो; पुढील फरक १२ म्हणून ३० + १२ = ४२."], "medium"),
  q("q13", "c_rajyaseva", "s_csat", "ch_r1", ["If CAT is coded as DBU, how is DOG coded?", "जर CAT हा शब्द DBU असा लिहिला, तर DOG कसा लिहाल?"], [["EPH", "EPH"], ["CNF", "CNF"], ["EPF", "EPF"], ["FQI", "FQI"]], 0, ["Each letter moves forward by one: D→E, O→P, G→H.", "प्रत्येक अक्षर एकने पुढे जाते: D→E, O→P, G→H."]),
  q("q14", "c_police", "s_pol_mar", "ch_pmr1", ["Choose the synonym of 'सूर्य' (sun).", "'सूर्य' या शब्दाचा समानार्थी शब्द निवडा."], [["Chandra (चंद्र)", "चंद्र"], ["Ravi (रवी)", "रवी"], ["Megh (मेघ)", "मेघ"], ["Tara (तारा)", "तारा"]], 1, ["'रवी' means sun.", "'रवी' म्हणजे सूर्य."]),
  q("q15", "c_psi", "s_psi_eng", "ch_pe1", ["Choose the antonym of 'Ancient'.", "'Ancient' या शब्दाचा विरुद्धार्थी शब्द निवडा."], [["Old", "Old (जुने)"], ["Modern", "Modern (आधुनिक)"], ["Antique", "Antique (प्राचीन वस्तू)"], ["Historic", "Historic (ऐतिहासिक)"]], 1, ["Ancient (very old) is the opposite of Modern.", "Ancient (अतिप्राचीन) चा विरुद्धार्थी Modern (आधुनिक)."]),
  q("q16", "c_police", "s_pol_gk", "ch_pg1", ["How many districts does Maharashtra have?", "महाराष्ट्रात एकूण किती जिल्हे आहेत?"], [["34", "३४"], ["35", "३५"], ["36", "३६"], ["38", "३८"]], 2, ["Maharashtra has 36 districts.", "महाराष्ट्रात ३६ जिल्हे आहेत."]),
  q("q17", "c_police", "s_pol_math", "ch_pm1", ["What is 25% of 480?", "४८० चे २५% किती?"], [["100", "१००"], ["120", "१२०"], ["125", "१२५"], ["140", "१४०"]], 1, ["25% = 1/4, and 480 ÷ 4 = 120.", "२५% = १/४; ४८० ÷ ४ = १२०."]),
  q("q18", "c_rajyaseva", "s_polity", "ch_p2", ["State Public Service Commissions are provided for under which Article?", "राज्य लोकसेवा आयोगाची तरतूद कोणत्या कलमात आहे?"], [["Article 312", "कलम ३१२"], ["Article 315", "कलम ३१५"], ["Article 324", "कलम ३२४"], ["Article 280", "कलम २८०"]], 1, ["Article 315 provides for Public Service Commissions for the Union and the States.", "कलम ३१५ मध्ये संघ व राज्यांसाठी लोकसेवा आयोगांची तरतूद आहे."], "hard"),
  q("q19", "c_rajyaseva", "s_polity", "ch_p1", ["What is the minimum age to become a member of the Lok Sabha?", "लोकसभा सदस्य होण्यासाठी किमान वय किती?"], [["21 years", "२१ वर्षे"], ["25 years", "२५ वर्षे"], ["30 years", "३० वर्षे"], ["35 years", "३५ वर्षे"]], 1, ["Article 84: minimum 25 years for the Lok Sabha.", "कलम ८४: लोकसभेसाठी किमान वय २५ वर्षे."]),
  q("q20", "c_police", "s_pol_gk", "ch_pg1", ["Which city of Maharashtra is known as the 'Orange City'?", "महाराष्ट्रातील कोणते शहर 'संत्रानगरी' म्हणून ओळखले जाते?"], [["Nashik", "नाशिक"], ["Amravati", "अमरावती"], ["Nagpur", "नागपूर"], ["Akola", "अकोला"]], 2, ["Nagpur is famous for its oranges.", "नागपूर संत्र्यांसाठी प्रसिद्ध आहे."]),
];

const tests: T.Test[] = [
  { id: "t_mock1", title: L("Rajyaseva Mini Mock Test 1", "राज्यसेवा लघु सराव चाचणी १"), type: "mock", courseId: "c_rajyaseva", batchIds: ["b_rs_m", "b_rs_e"], questionIds: ["q1", "q2", "q3", "q4", "q5", "q6", "q7", "q8", "q9", "q10", "q11", "q12", "q13", "q18", "q19"], durationMin: 15, negativeMark: 0.5, passPercent: 40, attemptsAllowed: 2, availableFrom: dayOffset(-5), availableTo: dayOffset(10), resultMode: "immediate", resultsPublished: true, showExplanations: true, shuffle: true, status: "published" },
  { id: "t_polity", title: L("Polity Chapter Test", "राज्यव्यवस्था प्रकरण चाचणी"), type: "chapter", courseId: "c_rajyaseva", batchIds: ["b_rs_m", "b_rs_e"], questionIds: ["q6", "q7", "q8", "q18", "q19"], durationMin: 8, negativeMark: 0.5, passPercent: 40, attemptsAllowed: 1, availableFrom: dayOffset(-12), availableTo: dayOffset(-2), resultMode: "manual", resultsPublished: true, showExplanations: true, shuffle: false, status: "published" },
  { id: "t_daily", title: L("Daily Practice – Reasoning", "दैनंदिन सराव – बुद्धिमत्ता"), type: "daily", courseId: "c_rajyaseva", batchIds: ["b_rs_m", "b_rs_e"], questionIds: ["q12", "q13", "q10"], durationMin: 5, negativeMark: 0, passPercent: 33, attemptsAllowed: 3, availableFrom: dayOffset(0), availableTo: dayOffset(1), resultMode: "immediate", resultsPublished: true, showExplanations: true, shuffle: true, status: "published" },
  { id: "t_pol1", title: L("Police Bharti Weekly Test 1", "पोलीस भरती साप्ताहिक चाचणी १"), type: "weekly", courseId: "c_police", batchIds: ["b_pol"], questionIds: ["q14", "q16", "q17", "q20", "q1"], durationMin: 6, negativeMark: 0, passPercent: 40, attemptsAllowed: 1, availableFrom: dayOffset(-1), availableTo: dayOffset(6), resultMode: "manual", resultsPublished: false, showExplanations: false, shuffle: true, status: "published" },
  { id: "t_grand", title: L("Grand Test (Draft)", "महा सराव चाचणी (मसुदा)"), type: "grand", courseId: "c_rajyaseva", batchIds: ["b_rs_m"], questionIds: ["q1", "q2", "q3", "q4", "q5"], durationMin: 60, negativeMark: 0.5, passPercent: 40, attemptsAllowed: 1, availableFrom: dayOffset(20), availableTo: dayOffset(21), resultMode: "manual", resultsPublished: false, showExplanations: false, shuffle: true, status: "draft" },
];

const libraryPlans: T.LibraryPlan[] = [
  { id: "lp_seat", name: L("Library seat only", "फक्त अभ्यासिका आसन"), months: 1, fee: 800, registrationFee: 200, kind: "library", locker: false, spaciousSeat: false, active: true },
  { id: "lp_locker", name: L("Library seat + locker", "अभ्यासिका आसन + लॉकर"), months: 1, fee: 1000, registrationFee: 200, kind: "library", locker: true, spaciousSeat: false, active: true },
  { id: "lp_spacious", name: L("Spacious seat", "प्रशस्त आसन"), months: 1, fee: 1200, registrationFee: 200, kind: "library", locker: false, spaciousSeat: true, active: true },
  { id: "lp_bundle", name: L("Course + library", "अभ्यासक्रम + अभ्यासिका केंद्र"), months: 12, fee: 50000, registrationFee: 500, kind: "bundle", bundleCourseId: "c_rajyaseva", locker: false, spaciousSeat: false, active: true },
];

const settings: T.Settings = {
  gateways: [
    { id: "gw_upi", name: "UPI / QR (static)", enabled: true, isDefault: true, configured: false },
    { id: "gw_razorpay", name: "Payment Gateway A (e.g. Razorpay)", enabled: true, isDefault: false, configured: false },
    { id: "gw_cashfree", name: "Payment Gateway B (e.g. Cashfree/PayU)", enabled: false, isDefault: false, configured: false },
  ],
  gst: { enabled: false, rate: 18, inclusive: true, gstin: "" },
  attendance: { minimumPercent: 75, warningPercent: 80, qrValiditySec: 30 },
  reminderDays: [7, 3, 1],
  channels: { inapp: true, whatsapp: false, sms: false, email: false, telegram: false },
  eventChannels: {
    admission: ["inapp", "whatsapp", "sms"], payment: ["inapp", "whatsapp", "email"], installment: ["inapp", "whatsapp", "sms"],
    liveClass: ["inapp", "whatsapp"], material: ["inapp"], test: ["inapp", "telegram"], result: ["inapp", "whatsapp"],
    attendance: ["inapp", "sms"], announcement: ["inapp", "whatsapp", "telegram"], examUpdate: ["inapp", "telegram"], library: ["inapp", "whatsapp"],
  },
  referral: { enabled: false, rewardType: "flat", referrerReward: 500, refereeDiscount: 500, trigger: "fullPayment", validTo: dayOffset(90), limitPerStudent: 5 },
  transferNeedsApproval: true,
  materialApproval: true,
  receiptPrefix: "VA",
  cms: {
    homeSections: DEFAULT_HOME_SECTIONS,
    director: DEFAULT_DIRECTOR,
    vision: L("", ""),
    mission: L("", ""),
    stats: DEFAULT_STATS,
    heroTitle: L("Your dream… our direction.", "स्वप्न तुमचे... दिशा आमची"),
    heroSubtitle: L(
      "Nikhil Rudrawar's Vidyadhan Academy – focused guidance for MPSC, PSI, STI, ASO and other Maharashtra competitive examinations. Not just a class — a family.",
      "निखिल रुद्रवार यांची विद्याधन अकॅडमी – एमपीएससी, पीएसआय, एसटीआय, एएसओ व इतर महाराष्ट्र स्पर्धा परीक्षांसाठी लक्ष्यकेंद्रित मार्गदर्शन. क्लास नव्हे, एक कुटुंबच!"
    ),
    whyUs: [
      L("Exam-focused syllabus planning", "परीक्षाकेंद्रित अभ्यासक्रम नियोजन"),
      L("Regular tests with detailed analysis", "सविस्तर विश्लेषणासह नियमित चाचण्या"),
      L("Marathi & English study material", "मराठी व इंग्रजी अभ्यास साहित्य"),
      L("Offline, online and hybrid batches", "ऑफलाइन, ऑनलाइन व हायब्रिड बॅचेस"),
      L("Quiet physical Abhyasika Kendra for self-study", "स्वयं-अभ्यासासाठी शांत अभ्यासिका केंद्र"),
      L("Personal attention & doubt solving", "वैयक्तिक लक्ष व शंका निरसन"),
    ],
    aboutBody: L(
      "Nikhil Rudrawar's Vidyadhan Academy prepares aspirants for MPSC, PSI, STI and ASO examinations. Our belief is simple: this is not just a class, it is a family – your dream, our direction. [Add founding year, director's message and approach.]",
      "निखिल रुद्रवार यांची विद्याधन अकॅडमी एमपीएससी, पीएसआय, एसटीआय व एएसओ परीक्षांची तयारी करून घेते. आमचा विश्वास साधा आहे – क्लास नव्हे, एक कुटुंबच! स्वप्न तुमचे... दिशा आमची. [स्थापना वर्ष, संचालकांचा संदेश व कार्यपद्धती जोडा.]"
    ),
    libraryBlurb: L(
      "A calm, disciplined reading space with allotted seats, available as a stand-alone membership or bundled with a course.",
      "नियोजित आसनव्यवस्थेसह शांत, शिस्तबद्ध अभ्यासिका — स्वतंत्र सदस्यत्व किंवा अभ्यासक्रमासोबत संच म्हणून उपलब्ध."
    ),
  },
  seo: {
    home: { title: "Vidyadhan Academy | MPSC, Police Bharti & Competitive Exam Coaching", description: "Competitive exam coaching in Maharashtra – MPSC Rajyaseva, PSI/STI/ASO, Police Bharti, Talathi and more." },
    courses: { title: "Courses | Vidyadhan Academy", description: "Explore competitive exam courses and batches." },
    library: { title: "Abhyasika Kendra Membership | Vidyadhan Academy", description: "Abhyasika Kendra with seat allocation." },
    contact: { title: "Contact | Vidyadhan Academy", description: "Contact Vidyadhan Academy for admissions." },
  },
};

export function buildSeed() {
  const rnd = seeded(42);
  const students: T.Student[] = [];
  const enrolments: T.Enrolment[] = [];
  const payments: T.Payment[] = [];
  const memberships: T.LibraryMembership[] = [];
  const checkins: T.LibraryCheckin[] = [];
  const attendance: T.AttendanceRecord[] = [];
  const attempts: T.Attempt[] = [];
  let receipt = 1001;
  const batchPool = ["b_rs_m", "b_rs_m", "b_rs_e", "b_psi", "b_pol", "b_pol"];

  for (let i = 0; i < 24; i++) {
    const id = `st_${String(i + 1).padStart(3, "0")}`;
    const name = `${FIRST[i]} ${LAST[i % LAST.length]}`;
    const s: T.Student = {
      id, code: `VA-2026-${String(i + 1).padStart(4, "0")}`, name, mobile: `98765${String(10000 + i * 137).slice(-5)}`,
      email: i % 3 === 0 ? `${FIRST[i].toLowerCase()}@example.com` : undefined, gender: i % 2 ? "male" : "female",
      branchId: "br_main", createdAt: dayOffset(-90 + i * 3), status: i === 20 ? "inactive" : "active",
      follows: i % 2 ? ["a_mpsc", "a_police"] : ["a_mpsc", "a_ssc"], bookmarks: [], contentLang: i % 4 === 0 ? "en" : "mr",
      parentMobile: i % 3 === 1 ? `97654${String(20000 + i * 91).slice(-5)}` : undefined, parentAlerts: i % 3 === 1,
      consent: { whatsapp: true, sms: true, email: !!(i % 3 === 0), publishResults: i % 5 === 0 }, referralCode: `VA${(1000 + i * 7).toString(36).toUpperCase()}`,
      telegramLinked: i % 4 === 0, dataSaver: false,
    };
    students.push(s);
    if (i >= 22) continue; // two students are Abhyasika Kendra only members
    const batchId = i === 0 ? "b_rs_m" : batchPool[Math.floor(rnd() * batchPool.length)];
    const b = batches.find((x) => x.id === batchId)!;
    const discount = i % 5 === 0 ? 3000 : i % 7 === 0 ? 5000 : 0;
    const finalFee = b.fee - discount;
    const plan = Math.floor(rnd() * 3); // 0 full, 1 partial, 2 pending
    const paid = plan === 0 ? finalFee : plan === 1 ? Math.round(finalFee * 0.5) : 0;
    const enrId = `en_${id}`;
    enrolments.push({
      id: enrId, studentId: id, courseId: b.courseId, batchId, branchId: "br_main", originalFee: b.fee, discount,
      discountReason: discount === 3000 ? "Early-bird offer (demo)" : discount ? "Scholarship – merit (demo)" : "", discountApprovedBy: discount ? "u_owner" : undefined,
      finalFee, installments: [{ due: dayOffset(-40 + i), amount: Math.round(finalFee / 2) }, { due: dayOffset(15 + i), amount: finalFee - Math.round(finalFee / 2) }],
      paymentStatus: paid === 0 ? "pending" : paid < finalFee ? "partial" : "paid", admissionStatus: paid > 0 ? "confirmed" : "pending",
      enrolmentStatus: i === 20 ? "suspended" : "active", createdAt: s.createdAt, history: [{ at: s.createdAt, text: "Enrolment created", by: "Office Staff (Demo)" }],
    });
    if (paid > 0) {
      payments.push({ id: `pay_${id}`, receiptNo: `VA/2026-27/${receipt++}`, studentId: id, enrolmentId: enrId, amount: paid, method: (["cash", "upi", "gateway", "bank"] as T.PayMethod[])[i % 4], reference: i % 4 === 0 ? "CASH" : `TXN${700000 + i * 13}`, date: dayOffset(-60 + i * 2), recordedBy: "u_office", status: "success", reprints: 0 });
    }
    // attendance: last 20 working days
    for (let d = 1; d <= 24; d++) {
      const date = dayOffset(-d);
      if (new Date(date).getDay() === 0) continue;
      if (new Date(date) < new Date(b.startDate)) continue;
      const r = rnd();
      const bias = i === 3 || i === 11 ? 0.45 : 0.12; // a few students with low attendance
      attendance.push({ id: `at_${id}_${d}`, batchId, date, studentId: id, status: r < bias ? "A" : r < bias + 0.08 ? "L" : "P", method: rnd() < 0.4 ? "qr" : "manual", markedBy: b.facultyIds[0] });
    }
  }
  // library memberships
  const libStudents = [0, 2, 5, 9, 14, 22, 23];
  libStudents.forEach((si, k) => {
    const sid = students[si].id;
    const plan = libraryPlans[k % 4];
    const start = dayOffset(-plan.months * 30 + 10 - k * 3);
    const end = addMonths(start, plan.months);
    const status: T.MembershipStatus = new Date(end) < new Date() ? "expired" : "active";
    const mid = `lm_${sid}`;
    memberships.push({ id: mid, studentId: sid, planId: plan.id, start, end, status, seat: `S-${String(k * 3 + 4).padStart(2, "0")}`, fee: plan.fee, discount: 0, createdAt: start });
    payments.push({ id: `pay_${mid}`, receiptNo: `VA/2026-27/${receipt++}`, studentId: sid, membershipId: mid, amount: plan.fee, method: "upi", reference: `UPI${880000 + k}`, date: start, recordedBy: "u_office", status: "success", reprints: 0 });
    if (status === "active") checkins.push({ id: `ci_${mid}`, membershipId: mid, date: dayOffset(0), inTime: "08:1" + k });
  });
  // past test attempts for ranking
  const polityQ = ["q6", "q7", "q8", "q18", "q19"];
  students.slice(0, 14).forEach((s, i) => {
    const e = enrolments.find((x) => x.studentId === s.id);
    if (!e || e.courseId !== "c_rajyaseva") return;
    const answers: Record<string, number | null> = {};
    let correct = 0, incorrect = 0, un = 0;
    polityQ.forEach((qid) => {
      const qq = questions.find((x) => x.id === qid)!;
      const r = rnd();
      if (r < 0.12) { answers[qid] = null; un++; }
      else if (r < 0.7 - i * 0.01) { answers[qid] = qq.answer; correct++; }
      else { answers[qid] = (qq.answer + 1) % 4; incorrect++; }
    });
    attempts.push({ id: `atp_${s.id}`, testId: "t_polity", studentId: s.id, answers, review: [], order: polityQ, startedAt: dayOffset(-8) + "T10:00:00", submittedAt: dayOffset(-8) + "T10:07:00", score: correct * 2 - incorrect * 0.5, correct, incorrect, unanswered: un, tabSwitches: i === 4 ? 3 : 0 });
  });

  const enquiries: T.Enquiry[] = [
    { id: "eq_1", type: "quick", name: "Swapnil Kadam", mobile: "9822011111", courseId: "c_rajyaseva", status: "new", notes: [], createdAt: dayOffset(0) + "T09:30:00", source: "website" },
    { id: "eq_2", type: "detailed", name: "Neha Salunkhe", mobile: "9822022222", email: "neha@example.com", courseId: "c_psi", batchId: "b_psi", qualification: "B.Com", message: "Is there a weekend option?", status: "contacted", assignedTo: "u_office", followUpDate: dayOffset(2), notes: [{ at: dayOffset(-1) + "T12:00:00", by: "Office Staff (Demo)", text: "Called; will visit on Saturday." }], createdAt: dayOffset(-2) + "T16:10:00", source: "website" },
    { id: "eq_3", type: "quick", name: "Amol Thorat", mobile: "9822033333", courseId: "c_police", status: "followup", assignedTo: "u_office", followUpDate: dayOffset(0), notes: [{ at: dayOffset(-3) + "T11:00:00", by: "Office Staff (Demo)", text: "Asked about fee instalments." }], createdAt: dayOffset(-4) + "T10:00:00", source: "phone" },
    { id: "eq_4", type: "detailed", name: "Rani Mane", mobile: "9822044444", courseId: "c_rajyaseva", batchId: "b_rs_e", qualification: "B.A.", status: "converted", assignedTo: "u_office", notes: [], createdAt: dayOffset(-12) + "T10:00:00", source: "walk-in" },
    { id: "eq_5", type: "quick", name: "Yogesh Lokhande", mobile: "9822055555", courseId: "c_talathi", status: "notInterested", notes: [{ at: dayOffset(-5) + "T10:00:00", by: "Office Staff (Demo)", text: "Joined elsewhere." }], createdAt: dayOffset(-9) + "T10:00:00", source: "whatsapp" },
    { id: "eq_6", type: "quick", name: "Swapnil K.", mobile: "9822011111", courseId: "c_groupb", status: "new", notes: [], createdAt: dayOffset(0) + "T13:05:00", source: "website" },
    { id: "eq_7", type: "detailed", name: "Kavita Ingle", mobile: "9822077777", courseId: "c_groupc", qualification: "12th", status: "new", notes: [], createdAt: dayOffset(-1) + "T18:20:00", source: "website" },
  ];

  const materials: T.Material[] = [
    { id: "m_1", courseId: "c_rajyaseva", subjectId: "s_polity", chapterId: "ch_p1", batchIds: ["b_rs_m", "b_rs_e"], title: L("Fundamental Rights – summary notes", "मूलभूत हक्क – सारांश नोट्स"), kind: "pdf", sizeKb: 1840, access: "both", status: "approved", uploadedBy: "u_fac1", uploadedAt: dayOffset(-10), availableFrom: dayOffset(-10), lang: "both", downloads: 41, watermark: true },
    { id: "m_2", courseId: "c_rajyaseva", subjectId: "s_hist", chapterId: "ch_h3", batchIds: ["b_rs_m", "b_rs_e"], title: L("Social reformers – chart", "समाजसुधारक – तक्ता"), kind: "image", sizeKb: 620, access: "view", status: "approved", uploadedBy: "u_fac1", uploadedAt: dayOffset(-6), availableFrom: dayOffset(-6), lang: "mr", downloads: 0, watermark: true },
    { id: "m_3", courseId: "c_rajyaseva", subjectId: "s_csat", chapterId: "ch_r1", batchIds: ["b_rs_m"], title: L("Number series practice set", "संख्या मालिका सराव संच"), kind: "pdf", sizeKb: 2950, access: "download", status: "approved", uploadedBy: "u_fac2", uploadedAt: dayOffset(-3), availableFrom: dayOffset(-3), lang: "both", downloads: 18, watermark: false },
    { id: "m_4", courseId: "c_rajyaseva", subjectId: "s_geo", chapterId: "ch_g1", batchIds: ["b_rs_m", "b_rs_e"], title: L("Rivers of Maharashtra", "महाराष्ट्रातील नद्या"), kind: "pdf", sizeKb: 4100, access: "both", status: "pending", uploadedBy: "u_fac3", uploadedAt: dayOffset(0), availableFrom: dayOffset(0), lang: "mr", downloads: 0, watermark: true },
    { id: "m_5", courseId: "c_police", subjectId: "s_pol_math", chapterId: "ch_pm1", batchIds: ["b_pol"], title: L("Percentage shortcuts", "टक्केवारी शॉर्टकट्स"), kind: "notes", sizeKb: 300, access: "view", status: "approved", uploadedBy: "u_fac2", uploadedAt: dayOffset(-2), availableFrom: dayOffset(-2), lang: "mr", downloads: 0, watermark: false },
    { id: "m_6", courseId: "c_rajyaseva", subjectId: "s_eco", batchIds: ["b_rs_m"], title: L("Monetary policy – upcoming (scheduled)", "चलनविषयक धोरण – आगामी (नियोजित)"), kind: "pdf", sizeKb: 1500, access: "restricted", status: "approved", uploadedBy: "u_owner", uploadedAt: dayOffset(-1), availableFrom: dayOffset(5), lang: "en", downloads: 0, watermark: true },
  ];

  const live: T.LiveClass[] = [
    { id: "lc_1", batchId: "b_rs_e", subjectId: "s_polity", facultyId: "u_fac1", title: "Governor – powers & functions", date: dayOffset(0), time: "18:00", durationMin: 90, provider: "meet", link: "https://meet.google.com/demo-link", status: "scheduled", attendees: [] },
    { id: "lc_2", batchId: "b_rs_m", subjectId: "s_csat", facultyId: "u_fac2", title: "Coding-decoding drill", date: dayOffset(1), time: "08:00", durationMin: 60, provider: "zoom", link: "https://zoom.us/j/demo", status: "scheduled", attendees: [] },
    { id: "lc_3", batchId: "b_rs_m", subjectId: "s_hist", facultyId: "u_fac1", title: "Mahatma Phule – revision", date: dayOffset(-2), time: "08:00", durationMin: 60, provider: "meet", link: "https://meet.google.com/demo-link", status: "completed", attendees: ["st_001", "st_002"] },
    { id: "lc_4", batchId: "b_pol", subjectId: "s_pol_gk", facultyId: "u_fac3", title: "Maharashtra districts quiz", date: dayOffset(2), time: "07:00", durationMin: 45, provider: "meet", link: "https://meet.google.com/demo-link", status: "rescheduled", attendees: [] },
  ];
  const recordings: T.Recording[] = [
    { id: "rec_1", courseId: "c_rajyaseva", subjectId: "s_polity", chapterId: "ch_p1", title: L("Fundamental Rights – Part 1", "मूलभूत हक्क – भाग १"), durationMin: 52, source: "external", url: "https://www.youtube.com/", published: true, availableFrom: dayOffset(-20), batchIds: ["b_rs_m", "b_rs_e"] },
    { id: "rec_2", courseId: "c_rajyaseva", subjectId: "s_polity", chapterId: "ch_p1", title: L("Fundamental Rights – Part 2", "मूलभूत हक्क – भाग २"), durationMin: 47, source: "hosted", url: "", published: true, availableFrom: dayOffset(-18), batchIds: ["b_rs_m", "b_rs_e"] },
    { id: "rec_3", courseId: "c_rajyaseva", subjectId: "s_hist", chapterId: "ch_h2", title: L("1857 Revolt", "१८५७ चा उठाव"), durationMin: 61, source: "hosted", url: "", published: true, availableFrom: dayOffset(-9), batchIds: ["b_rs_m", "b_rs_e"] },
    { id: "rec_4", courseId: "c_rajyaseva", subjectId: "s_geo", title: L("Monsoon mechanism (unpublished)", "मान्सूनची यंत्रणा (अप्रकाशित)"), durationMin: 40, source: "hosted", url: "", published: false, availableFrom: dayOffset(3), batchIds: ["b_rs_m"] },
    { id: "rec_5", courseId: "c_police", subjectId: "s_pol_math", title: L("Percentages in 30 minutes", "३० मिनिटांत टक्केवारी"), durationMin: 30, source: "external", url: "https://www.youtube.com/", published: true, availableFrom: dayOffset(-4), batchIds: ["b_pol"] },
  ];
  const assignments: T.Assignment[] = [
    { id: "as_1", batchId: "b_rs_m", subjectId: "s_hist", title: "Essay: Contribution of Mahatma Phule", instructions: "Write 500 words. Upload a PDF or clear photos of handwritten pages.", attachments: ["guidelines.pdf"], issued: dayOffset(-5), due: dayOffset(2), maxMarks: 20, passMarks: 8, allowLate: true, allowResubmit: true, createdBy: "u_fac1" },
    { id: "as_2", batchId: "b_rs_m", subjectId: "s_csat", title: "Practice set 3 – series", instructions: "Solve all 25 questions with working.", attachments: [], issued: dayOffset(-10), due: dayOffset(-3), maxMarks: 25, passMarks: 10, allowLate: false, allowResubmit: false, createdBy: "u_fac2" },
    { id: "as_3", batchId: "b_pol", subjectId: "s_pol_mar", title: "Marathi grammar worksheet 1", instructions: "Complete the worksheet.", attachments: ["worksheet1.pdf"], issued: dayOffset(-2), due: dayOffset(4), maxMarks: 10, passMarks: 4, allowLate: true, allowResubmit: true, createdBy: "u_fac3" },
  ];
  const submissions: T.Submission[] = [
    { id: "sb_1", assignmentId: "as_2", studentId: "st_001", files: [{ name: "set3.pdf", sizeKb: 820 }], submittedAt: dayOffset(-4) + "T20:00:00", late: false, status: "graded", marks: 21, feedback: "Good work. Revise mixed series." },
    { id: "sb_2", assignmentId: "as_1", studentId: "st_002", files: [{ name: "page1.jpg", sizeKb: 1200 }, { name: "page2.jpg", sizeKb: 1100 }], submittedAt: dayOffset(-1) + "T21:00:00", late: false, status: "submitted" },
  ];
  const tickets: T.Ticket[] = [
    { id: "tk_1", code: "TKT-1001", studentId: "st_001", category: "fees", subject: "Receipt not received for second instalment", priority: "medium", status: "inProgress", assignedTo: "u_office", messages: [{ by: "st_001", text: "I paid by UPI yesterday but have not received a receipt.", at: dayOffset(-1) + "T10:00:00" }, { by: "u_office", text: "We are verifying the transaction and will update you today.", at: dayOffset(-1) + "T12:30:00" }], createdAt: dayOffset(-1) + "T10:00:00" },
    { id: "tk_2", code: "TKT-1002", studentId: "st_004", category: "technical", subject: "Video not loading on mobile data", priority: "low", status: "new", messages: [{ by: "st_004", text: "Recorded lecture keeps buffering.", at: dayOffset(0) + "T08:00:00" }], createdAt: dayOffset(0) + "T08:00:00" },
  ];
  const inbox: T.InboxItem[] = [
    { id: "in_1", studentId: "*", title: L("Diwali holiday schedule", "दिवाळी सुट्टी वेळापत्रक"), body: L("Classes will remain closed as per the published schedule (demo).", "प्रकाशित वेळापत्रकानुसार वर्ग बंद राहतील (डेमो)."), at: dayOffset(-1) + "T09:00:00", read: false, kind: "general" },
    { id: "in_2", studentId: "st_001", title: L("Live class today at 6:00 PM", "आज संध्याकाळी ६ वाजता लाईव्ह वर्ग"), body: L("Governor – powers & functions", "राज्यपाल – अधिकार व कार्ये"), at: dayOffset(0) + "T08:00:00", read: false, kind: "class" },
    { id: "in_3", studentId: "st_001", title: L("New study material", "नवीन अभ्यास साहित्य"), body: L("Number series practice set has been published.", "संख्या मालिका सराव संच प्रकाशित झाला आहे."), at: dayOffset(-3) + "T10:00:00", read: true, kind: "material" },
    { id: "in_4", studentId: "st_001", title: L("Instalment due soon", "हप्ता लवकरच देय"), body: L("Your next instalment is due in 15 days.", "आपला पुढील हप्ता १५ दिवसांत देय आहे."), at: dayOffset(-2) + "T10:00:00", read: false, kind: "payment" },
  ];
  const broadcasts: T.Broadcast[] = [
    { id: "bc_1", title: "Diwali holiday schedule", body: "Classes closed as per schedule.", channels: ["inapp"], audience: { type: "all", ids: [] }, createdAt: dayOffset(-1) + "T09:00:00", createdBy: "u_owner", status: "sent", delivered: 24, failed: 0 },
    { id: "bc_2", title: "Police Bharti weekly test reminder", body: "Weekly test opens tomorrow.", channels: ["inapp", "sms"], audience: { type: "batch", ids: ["b_pol"] }, createdAt: dayOffset(-2) + "T09:00:00", createdBy: "u_office", status: "sent", delivered: 6, failed: 1 },
    { id: "bc_3", title: "Group B batch starts", body: "Orientation session details.", channels: ["inapp", "whatsapp"], audience: { type: "course", ids: ["c_groupb"] }, scheduledAt: dayOffset(10) + "T09:00", createdAt: dayOffset(0) + "T09:00:00", createdBy: "u_office", status: "scheduled", delivered: 0, failed: 0 },
  ];
  // REAL results supplied by the academy (images in /public/results). Add more here or via Admin → Results & Achievements.
  const achievements: T.Achievement[] = [
    {
      id: "ac_arun_gawade", name: L("Major Arun Gawade Sir", "मेजर अरुण गावडे सर"), exam: L("Mumbai Police", "मुंबई पोलीस"),
      result: L("Selected – Mumbai Police", "मुंबई पोलीस पदी निवड"), photo: "/results/arun-gawade-mumbai-police.jpg",
      consent: true, consentNote: "Published by the academy on its own poster", published: true, demo: false, featured: true,
    },
    ...structuredClone(SAMPLE_ACHIEVEMENTS),
  ];
  const testimonials: T.Testimonial[] = [];
  const announcements: T.Announcement[] = [
    { id: "an_1", title: L("Admissions open: Rajyaseva evening hybrid batch", "प्रवेश सुरू: राज्यसेवा सायंकाळ हायब्रिड बॅच"), body: L("Limited seats. Visit the office or apply online. (Demo)", "मर्यादित जागा. कार्यालयात भेट द्या किंवा ऑनलाइन अर्ज करा. (डेमो)"), date: dayOffset(-2), pinned: true, published: true },
    { id: "an_2", title: L("Group B online batch starts soon", "गट ब ऑनलाइन बॅच लवकरच सुरू"), body: L("Orientation details will be shared with registered students. (Demo)", "नोंदणीकृत विद्यार्थ्यांना परिचय सत्राची माहिती दिली जाईल. (डेमो)"), date: dayOffset(-5), pinned: false, published: true },
    { id: "an_3", title: L("Abhyasika Kendra: new seats added", "अभ्यासिका केंद्र: नवीन आसने उपलब्ध"), body: L("Additional seats are available for the annual plan. (Demo)", "वार्षिक योजनेसाठी अतिरिक्त आसने उपलब्ध. (डेमो)"), date: dayOffset(-8), pinned: false, published: true },
  ];
  const faqs: T.Faq[] = [
    { id: "fq_1", q: L("Can I pay the fee in instalments?", "शुल्क हप्त्यांमध्ये भरता येते का?"), a: L("Yes. Instalment schedules are shown during admission and in your Fees section.", "होय. हप्त्यांचे वेळापत्रक प्रवेशावेळी व 'शुल्क' विभागात दिसते.") },
    { id: "fq_2", q: L("Can I study in Marathi and switch to English later?", "मी मराठीत अभ्यास करून नंतर इंग्रजीत बदलू शकतो का?"), a: L("Yes. Content language is independent of the website language and can be changed from your profile.", "होय. अभ्यास साहित्याची भाषा संकेतस्थळाच्या भाषेपेक्षा स्वतंत्र असून प्रोफाइलमधून बदलता येते.") },
    { id: "fq_3", q: L("Is Aadhaar mandatory for admission?", "प्रवेशासाठी आधार अनिवार्य आहे का?"), a: L("No. Only your name and mobile number are required.", "नाही. फक्त नाव आणि मोबाईल क्रमांक आवश्यक आहे.") },
    { id: "fq_4", q: L("Can I take only the Abhyasika Kendra membership?", "मला फक्त अभ्यासिका केंद्र सदस्यत्व घेता येईल का?"), a: L("Yes. Abhyasika Kendra only plans are available, as well as discounted course + Abhyasika Kendra bundles.", "होय. फक्त अभ्यासिका केंद्र योजना तसेच सवलतीच्या अभ्यासक्रम + अभ्यासिका केंद्र संच उपलब्ध आहेत.") },
  ];
  const facultyProfiles: T.FacultyProfile[] = [
    { id: "fp_1", staffId: "u_fac1", name: "[Faculty name]", subject: L("History & Polity", "इतिहास व राज्यव्यवस्था"), bio: L("Profile to be provided by the academy.", "प्रोफाइल अकॅडमीकडून उपलब्ध होईल."), published: true, demo: true },
    { id: "fp_2", staffId: "u_fac2", name: "[Faculty name]", subject: L("CSAT & Arithmetic", "सीसॅट व अंकगणित"), bio: L("Profile to be provided by the academy.", "प्रोफाइल अकॅडमीकडून उपलब्ध होईल."), published: true, demo: true },
    { id: "fp_3", staffId: "u_fac3", name: "[Faculty name]", subject: L("Marathi, Geography & GK", "मराठी, भूगोल व सामान्य ज्ञान"), bio: L("Profile to be provided by the academy.", "प्रोफाइल अकॅडमीकडून उपलब्ध होईल."), published: true, demo: true },
  ];
  const promos: T.PromoCode[] = [
    { id: "pr_1", code: "EARLY3000", kind: "earlybird", type: "flat", value: 3000, validFrom: dayOffset(-30), validTo: dayOffset(15), courseIds: ["c_rajyaseva", "c_psi"], limit: 50, used: 5, minPayment: 10000, needsApproval: false, active: true },
    { id: "pr_2", code: "DIWALI10", kind: "seasonal", type: "percent", value: 10, validFrom: dayOffset(-5), validTo: dayOffset(10), courseIds: [], limit: 100, used: 3, minPayment: 0, needsApproval: false, active: true },
    { id: "pr_3", code: "MERIT", kind: "scholarship", type: "percent", value: 25, validFrom: dayOffset(-90), validTo: dayOffset(180), courseIds: [], limit: 10, used: 2, minPayment: 0, needsApproval: true, active: true },
  ];
  const certificates: T.Certificate[] = [
    { id: "ce_1", number: "VA-CERT-2026-0001", studentId: "st_005", kind: "participation", title: "Polity Chapter Test", issuedAt: dayOffset(-7), reissues: 0 },
  ];
  const referrals: T.Referral[] = [
    { id: "rf_1", referrerId: "st_001", name: "Friend (demo)", mobile: "9822099999", status: "enquiry", reward: 0, rewardStatus: "none", createdAt: dayOffset(-3) },
  ];
  const audit: T.AuditEntry[] = [
    { id: "au_1", at: dayOffset(-2) + "T10:15:00", actor: "Owner (Demo)", action: "permission.update", entity: "Faculty – Reasoning (Demo)", detail: "Granted Results: view, Question bank: manage" },
    { id: "au_2", at: dayOffset(-1) + "T12:00:00", actor: "Office Staff (Demo)", action: "payment.record", entity: "VA/2026-27/1015", detail: "Recorded UPI payment" },
    { id: "au_3", at: dayOffset(-1) + "T15:40:00", actor: "Owner (Demo)", action: "discount.approve", entity: "Enrolment en_st_006", detail: "Merit scholarship ₹5,000 approved" },
  ];

  return {
    branches, classrooms, courses, subjects, batches, staff, students, enrolments, payments, enquiries,
    libraryPlans, memberships, checkins, materials, live, recordings, questions, tests, attempts,
    assignments, submissions, attendance, authorities, sources, updates, broadcasts, inbox, tickets,
    achievements, testimonials, announcements, faqs, facultyProfiles, promos, certificates, referrals,
    transfers: [] as T.TransferRequest[], audit,
    progress: { "st_001:rec_1": { sec: 1860, at: dayOffset(-1) + "T21:00:00" }, "st_001:rec_3": { sec: 600, at: dayOffset(0) + "T07:00:00" } } as Record<string, { sec: number; at: string }>,
    qrSessions: [] as { id: string; batchId: string; code: string; expiresAt: number; createdBy: string }[],
    downloads: [] as { materialId: string; studentId: string; at: string }[],
    tabSwitchLog: [] as { attemptId: string; at: string }[], settings, receiptSeq: receipt, syllabusHistory: [] as { courseId: string; version: number; at: string; by: string; note: string }[],
  };
}

export type DB = ReturnType<typeof buildSeed>;
