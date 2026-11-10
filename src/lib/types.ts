/** Bilingual text. Marathi and English are maintained separately (no auto-translation without review). */
export type L = { en: string; mr: string };
export type Lang = "en" | "mr";

export type Role = "visitor" | "student" | "faculty" | "staff" | "owner";
export type Access = "none" | "view" | "manage";

export const MODULES = [
  "enquiries",
  "students",
  "admissions",
  "courses",
  "batches",
  "fees",
  "library",
  "materials",
  "classes",
  "questionBank",
  "exams",
  "assignments",
  "attendance",
  "examUpdates",
  "notifications",
  "support",
  "results",
  "referrals",
  "certificates",
  "reports",
  "cms",
  "settings",
  "staff",
  "audit",
] as const;
export type Module = (typeof MODULES)[number];
export type Permissions = Record<Module, Access>;

export type Mode = "offline" | "online" | "hybrid";

export interface Branch { id: string; name: L; city: string; active: boolean }
export interface Classroom { id: string; branchId: string; name: string; capacity: number }

export interface Chapter { id: string; name: L; topics: L[] }
export interface Subject { id: string; courseId: string; name: L; chapters: Chapter[] }

export interface Course {
  id: string;
  slug: string;
  category: "state" | "police" | "revenue" | "combined" | "other";
  name: L;
  short: L;
  description: L;
  durationMonths: number;
  mediums: Lang[];
  fee: number;
  admissionOpen: boolean;
  showFee?: boolean; // public website hides the fee when false
  published: boolean;
  color: string; // tailwind gradient key for the placeholder image
  seo: { title: string; description: string };
  documentsRequired: string[];
  syllabusVersion: number;
}

export interface Batch {
  id: string;
  courseId: string;
  branchId: string;
  name: string;
  mode: Mode;
  startDate: string;
  endDate: string;
  days: string;
  time: string;
  capacity: number;
  facultyIds: string[];
  classroomId?: string;
  onlineLink?: string;
  fee: number;
  admissionOpen: boolean;
  admissionCloses: string;
}

export interface StaffUser {
  id: string;
  name: string;
  mobile: string;
  role: "faculty" | "staff" | "owner";
  designation: string;
  subjects: string[];
  batchIds: string[];
  permissions: Permissions;
  publishWithoutApproval: boolean;
  active: boolean;
}

export interface Student {
  id: string;
  code: string;
  name: string;
  mobile: string;
  email?: string;
  dob?: string;
  gender?: "male" | "female" | "other" | "";
  address?: string;
  parentMobile?: string;
  parentAlerts: boolean;
  branchId: string;
  createdAt: string;
  status: "active" | "inactive";
  follows: string[]; // authority ids
  bookmarks: string[]; // exam update ids
  contentLang: Lang;
  consent: { whatsapp: boolean; sms: boolean; email: boolean; publishResults: boolean };
  referralCode: string;
  telegramLinked: boolean;
  dataSaver: boolean;
}

export type PaymentStatus = "pending" | "partial" | "paid" | "failed" | "refunded";
export type AdmissionStatus = "pending" | "confirmed" | "cancelled";
export type EnrolmentStatus = "active" | "suspended" | "completed";

export interface Enrolment {
  id: string;
  studentId: string;
  courseId: string;
  batchId: string;
  branchId: string;
  originalFee: number;
  discount: number;
  discountReason: string;
  discountApprovedBy?: string;
  finalFee: number;
  installments: { due: string; amount: number }[];
  paymentStatus: PaymentStatus;
  admissionStatus: AdmissionStatus;
  enrolmentStatus: EnrolmentStatus;
  createdAt: string;
  history: { at: string; text: string; by: string }[];
}

export type PayMethod = "cash" | "upi" | "gateway" | "bank";
export interface Payment {
  id: string;
  receiptNo: string;
  studentId: string;
  enrolmentId?: string;
  membershipId?: string;
  amount: number;
  method: PayMethod;
  gateway?: string;
  reference: string;
  date: string;
  recordedBy: string;
  status: "success" | "failed" | "refunded";
  reprints: number;
  note?: string;
}

export type EnquiryStatus = "new" | "contacted" | "followup" | "converted" | "notInterested";
export interface Enquiry {
  id: string;
  type: "quick" | "detailed";
  name: string;
  mobile: string;
  email?: string;
  courseId: string;
  batchId?: string;
  qualification?: string;
  message?: string;
  status: EnquiryStatus;
  assignedTo?: string;
  followUpDate?: string;
  notes: { at: string; by: string; text: string }[];
  createdAt: string;
  source: "website" | "walk-in" | "phone" | "whatsapp" | "referral";
  referralCode?: string;
}

export interface LibraryPlan {
  id: string;
  name: L;
  months: number;
  fee: number;
  registrationFee: number;
  locker: boolean;
  spaciousSeat: boolean;
  kind: "library" | "bundle";
  bundleCourseId?: string;
  active: boolean;
}
export type MembershipStatus = "active" | "expired" | "suspended" | "cancelled";
export interface LibraryMembership {
  id: string;
  studentId: string;
  planId: string;
  start: string;
  end: string;
  status: MembershipStatus;
  seat?: string;
  fee: number;
  discount: number;
  createdAt: string;
}
export interface LibraryCheckin { id: string; membershipId: string; date: string; inTime: string; outTime?: string }

export type MaterialAccess = "view" | "download" | "both" | "restricted";
export interface Material {
  id: string;
  courseId: string;
  subjectId: string;
  chapterId?: string;
  batchIds: string[];
  title: L;
  kind: "pdf" | "notes" | "image";
  sizeKb: number;
  access: MaterialAccess;
  status: "pending" | "approved" | "rejected";
  uploadedBy: string;
  uploadedAt: string;
  availableFrom: string;
  lang: Lang | "both";
  downloads: number;
  watermark: boolean;
}

export interface LiveClass {
  id: string;
  batchId: string;
  subjectId: string;
  facultyId: string;
  title: string;
  date: string;
  time: string;
  durationMin: number;
  provider: "zoom" | "meet" | "other";
  link: string;
  status: "scheduled" | "completed" | "cancelled" | "rescheduled";
  attendees: string[];
}

export interface Recording {
  id: string;
  courseId: string;
  subjectId: string;
  chapterId?: string;
  title: L;
  durationMin: number;
  source: "external" | "hosted";
  url: string;
  published: boolean;
  availableFrom: string;
  batchIds: string[];
}

export interface Question {
  id: string;
  courseId: string;
  subjectId: string;
  chapterId?: string;
  topic?: string;
  text: L;
  options: L[];
  answer: number;
  explanation: L;
  reference?: string;
  difficulty: "easy" | "medium" | "hard";
  marks: number;
  negative: number;
  image?: string;
}

export type TestType = "chapter" | "mock" | "pyq" | "daily" | "weekly" | "grand";
export interface Test {
  id: string;
  title: L;
  type: TestType;
  courseId: string;
  batchIds: string[];
  questionIds: string[];
  durationMin: number;
  negativeMark: number;
  passPercent: number;
  attemptsAllowed: number;
  availableFrom: string;
  availableTo: string;
  resultMode: "immediate" | "manual";
  resultsPublished: boolean;
  showExplanations: boolean;
  shuffle: boolean;
  status: "draft" | "published";
}

export interface Attempt {
  id: string;
  testId: string;
  studentId: string;
  answers: Record<string, number | null>;
  review: string[];
  order: string[];
  startedAt: string;
  submittedAt?: string;
  score: number;
  correct: number;
  incorrect: number;
  unanswered: number;
  tabSwitches: number;
}

export interface Assignment {
  id: string;
  batchId: string;
  subjectId: string;
  title: string;
  instructions: string;
  attachments: string[];
  issued: string;
  due: string;
  maxMarks: number;
  passMarks: number;
  allowLate: boolean;
  allowResubmit: boolean;
  createdBy: string;
}
export interface Submission {
  id: string;
  assignmentId: string;
  studentId: string;
  files: { name: string; sizeKb: number }[];
  submittedAt: string;
  late: boolean;
  status: "submitted" | "graded" | "correction";
  marks?: number;
  feedback?: string;
}

export type AttendanceMark = "P" | "A" | "L";
export interface AttendanceRecord {
  id: string;
  batchId: string;
  date: string;
  studentId: string;
  status: AttendanceMark;
  method: "manual" | "qr";
  markedBy: string;
  correction?: { reason: string; by: string; at: string; from: AttendanceMark };
}

export interface Authority { id: string; name: L; group: string; url: string }
export interface UpdateSource {
  id: string;
  name: string;
  kind: "website" | "x" | "rss" | "other";
  url: string;
  authorityId: string;
  active: boolean;
  integration: "not-configured" | "manual" | "configured";
}
export type ImportantDateKind = "appStart" | "appEnd" | "correction" | "admitCard" | "exam" | "answerKey" | "result";
export interface ExamUpdate {
  id: string;
  authorityId: string;
  sourceId?: string;
  title: L;
  summary: L;
  sourceUrl: string;
  status: "pending" | "published" | "rejected" | "archived";
  createdAt: string;
  urgent: boolean;
  dates: { kind: ImportantDateKind; date: string }[];
}

export type Channel = "inapp" | "whatsapp" | "sms" | "email" | "telegram";
export interface Broadcast {
  id: string;
  title: string;
  body: string;
  channels: Channel[];
  audience: { type: "all" | "course" | "batch" | "student"; ids: string[] };
  scheduledAt?: string;
  createdAt: string;
  createdBy: string;
  status: "sent" | "scheduled" | "failed";
  delivered: number;
  failed: number;
}
export interface InboxItem {
  id: string;
  studentId: string; // "*" for all
  title: L;
  body: L;
  at: string;
  read: boolean;
  kind: "payment" | "class" | "material" | "test" | "result" | "attendance" | "update" | "library" | "general";
}

export type TicketStatus = "new" | "open" | "inProgress" | "resolved" | "closed";
export interface Ticket {
  id: string;
  code: string;
  studentId: string;
  category: "admissions" | "fees" | "course" | "lectures" | "exams" | "attendance" | "technical" | "complaint";
  subject: string;
  priority: "low" | "medium" | "high" | "urgent";
  status: TicketStatus;
  assignedTo?: string;
  messages: { by: string; text: string; at: string; attachment?: string }[];
  createdAt: string;
}

export interface Achievement {
  id: string;
  name: L;
  exam: L;
  result: L;
  year?: number;
  photo?: string; // URL (public/results/...) or data URL uploaded in the prototype
  courseId?: string;
  consent: boolean;
  consentNote?: string;
  published: boolean;
  demo: boolean;
  featured?: boolean;
}
export interface Testimonial { id: string; name: string; course: string; text: L; consent: boolean; published: boolean; demo: boolean }
export interface Announcement { id: string; title: L; body: L; date: string; pinned: boolean; published: boolean }
export interface Faq { id: string; q: L; a: L }
export interface FacultyProfile { id: string; staffId?: string; name: string; subject: L; bio: L; published: boolean; demo: boolean }

export interface PromoCode {
  id: string;
  code: string;
  kind: "earlybird" | "promo" | "referral" | "seasonal" | "scholarship";
  type: "percent" | "flat";
  value: number;
  validFrom: string;
  validTo: string;
  courseIds: string[];
  limit: number;
  used: number;
  minPayment: number;
  needsApproval: boolean;
  active: boolean;
}

export interface Certificate {
  id: string;
  number: string;
  studentId: string;
  kind: "completion" | "participation" | "achievement";
  title: string;
  issuedAt: string;
  reissues: number;
}

export interface Referral {
  id: string;
  referrerId: string;
  name: string;
  mobile: string;
  status: "enquiry" | "admitted" | "paid" | "rejected";
  reward: number;
  rewardStatus: "none" | "pending" | "credited";
  createdAt: string;
}

export interface TransferRequest {
  id: string;
  enrolmentId: string;
  fromBatchId: string;
  toBatchId: string;
  reason: string;
  feeAdjustment: number;
  status: "pending" | "approved" | "rejected";
  requestedAt: string;
}

export interface AuditEntry { id: string; at: string; actor: string; action: string; entity: string; detail: string }

export type HomeSectionKey = "photos" | "courses" | "why" | "batches" | "results" | "reviews" | "library" | "cta";
export interface HomeSection { key: HomeSectionKey; enabled: boolean }
export interface HomePhoto { id: string; src: string; caption: L; enabled: boolean }

export interface Settings {
  gateways: { id: string; name: string; enabled: boolean; isDefault: boolean; configured: boolean }[];
  gst: { enabled: boolean; rate: number; inclusive: boolean; gstin: string };
  attendance: { minimumPercent: number; warningPercent: number; qrValiditySec: number };
  reminderDays: number[];
  channels: Record<Channel, boolean>;
  eventChannels: Record<string, Channel[]>;
  referral: { enabled: boolean; rewardType: "flat" | "percent"; referrerReward: number; refereeDiscount: number; trigger: "admission" | "fullPayment"; validTo: string; limitPerStudent: number };
  transferNeedsApproval: boolean;
  materialApproval: boolean;
  receiptPrefix: string;
  cms: {
    heroTitle: L;
    heroSubtitle: L;
    whyUs: L[];
    aboutBody: L;
    libraryBlurb: L;
    homeSections?: HomeSection[];
    homePhotos?: HomePhoto[];
  };
  seo: Record<string, { title: string; description: string }>;
}

export interface Session {
  role: Role;
  userId: string | null;
}
