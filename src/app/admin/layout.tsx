"use client";
import {
  Award, BarChart3, BookOpen, Building2, ClipboardCheck, CreditCard, Database, FileText, GraduationCap, Globe, HelpCircle, History, IdCard,
  Inbox, KeyRound, LayoutDashboard, Layers, Library, ListTree, Megaphone, MonitorPlay, Newspaper, PenSquare, Receipt, Settings, Share2, Trophy, UserCog, Users, UserPlus,
} from "lucide-react";
import { PortalShell, type NavGroup } from "@/components/PortalShell";
import { useStore } from "@/lib/store";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const db = useStore((s) => s.db);
  const newEnq = db.enquiries.filter((e) => e.status === "new").length;
  const pendingMat = db.materials.filter((m) => m.status === "pending").length;
  const pendingUpd = db.updates.filter((u) => u.status === "pending").length;
  const openTk = db.tickets.filter((t) => t.status === "new").length;
  const groups: NavGroup[] = [
    { en: "Overview", mr: "आढावा", items: [
      { href: "/admin", en: "Dashboard", mr: "डॅशबोर्ड", icon: LayoutDashboard },
      { href: "/admin/reports", en: "Reports", mr: "अहवाल", icon: BarChart3, module: "reports" },
    ]},
    { en: "Admissions", mr: "प्रवेश", items: [
      { href: "/admin/enquiries", en: "Enquiries", mr: "चौकशी", icon: Inbox, module: "enquiries", badge: newEnq },
      { href: "/admin/students", en: "Students", mr: "विद्यार्थी", icon: Users, module: "students" },
      { href: "/admin/admissions", en: "Admissions & Enrolments", mr: "प्रवेश व नोंदणी", icon: UserPlus, module: "admissions" },
    ]},
    { en: "Academics", mr: "शैक्षणिक", items: [
      { href: "/admin/courses", en: "Courses", mr: "अभ्यासक्रम", icon: GraduationCap, module: "courses" },
      { href: "/admin/syllabus", en: "Subjects & Syllabus", mr: "विषय व अभ्यासक्रम", icon: ListTree, module: "courses" },
      { href: "/admin/batches", en: "Batches", mr: "बॅचेस", icon: Layers, module: "batches" },
      { href: "/admin/branches", en: "Branches & Classrooms", mr: "शाखा व वर्गखोल्या", icon: Building2, module: "batches" },
      { href: "/admin/classes", en: "Live / Recorded Classes", mr: "लाईव्ह / रेकॉर्डेड वर्ग", icon: MonitorPlay, module: "classes" },
      { href: "/admin/materials", en: "Study Materials", mr: "अभ्यास साहित्य", icon: BookOpen, module: "materials", badge: pendingMat },
      { href: "/admin/questions", en: "Question Bank", mr: "प्रश्नपेढी", icon: Database, module: "questionBank" },
      { href: "/admin/exams", en: "Examinations", mr: "परीक्षा", icon: PenSquare, module: "exams" },
      { href: "/admin/results", en: "Exam Results", mr: "परीक्षा निकाल", icon: Trophy, module: "exams" },
      { href: "/admin/assignments", en: "Assignments", mr: "गृहपाठ", icon: FileText, module: "assignments" },
      { href: "/admin/attendance", en: "Attendance", mr: "उपस्थिती", icon: ClipboardCheck, module: "attendance" },
    ]},
    { en: "Finance & Abhyasika Kendra", mr: "वित्त व अभ्यासिका केंद्र", items: [
      { href: "/admin/fees", en: "Fees & Payments", mr: "शुल्क व पेमेंट", icon: CreditCard, module: "fees" },
      { href: "/admin/receipts", en: "Receipts", mr: "पावत्या", icon: Receipt, module: "fees" },
      { href: "/admin/library", en: "Abhyasika Kendra", mr: "अभ्यासिका केंद्र", icon: Library, module: "library" },
    ]},
    { en: "Engagement", mr: "संवाद", items: [
      { href: "/admin/exam-updates", en: "Official Exam Updates", mr: "अधिकृत परीक्षा अपडेट्स", icon: Newspaper, module: "examUpdates", badge: pendingUpd },
      { href: "/admin/notifications", en: "Notifications", mr: "सूचना", icon: Megaphone, module: "notifications" },
      { href: "/admin/tickets", en: "Support Tickets", mr: "मदत तक्रारी", icon: HelpCircle, module: "support", badge: openTk },
      { href: "/admin/achievements", en: "Results & Achievements", mr: "निकाल व यश", icon: Award, module: "results" },
      { href: "/admin/referrals", en: "Referrals", mr: "रेफरल", icon: Share2, module: "referrals" },
      { href: "/admin/certificates", en: "ID Cards & Certificates", mr: "ओळखपत्रे व प्रमाणपत्रे", icon: IdCard, module: "certificates" },
    ]},
    { en: "System", mr: "प्रणाली", items: [
      { href: "/admin/staff", en: "Faculty & Staff", mr: "प्राध्यापक व कर्मचारी", icon: UserCog, module: "staff" },
      { href: "/admin/permissions", en: "Permissions", mr: "परवानग्या", icon: KeyRound, module: "staff" },
      { href: "/admin/cms", en: "Website CMS", mr: "वेबसाइट CMS", icon: Globe, module: "cms" },
      { href: "/admin/settings", en: "Settings", mr: "सेटिंग्ज", icon: Settings, module: "settings" },
      { href: "/admin/audit", en: "Audit Logs", mr: "ऑडिट नोंदी", icon: History, module: "audit" },
    ]},
  ];
  return <PortalShell roles={["staff", "owner"]} groups={groups} title={{ en: "Administration", mr: "प्रशासन" }}>{children}</PortalShell>;
}
