"use client";
import { Bell, BookOpen, ClipboardCheck, Database, FileText, LayoutDashboard, MonitorPlay, PenSquare, Trophy, Users, Layers } from "lucide-react";
import { PortalShell, type NavGroup } from "@/components/PortalShell";

const groups: NavGroup[] = [
  { en: "Teaching", mr: "अध्यापन", items: [
    { href: "/faculty-portal", en: "Dashboard", mr: "डॅशबोर्ड", icon: LayoutDashboard },
    { href: "/faculty-portal/batches", en: "My Batches", mr: "माझ्या बॅचेस", icon: Layers },
    { href: "/faculty-portal/students", en: "Students (assigned)", mr: "विद्यार्थी (नियुक्त)", icon: Users, module: "students" },
    { href: "/faculty-portal/attendance", en: "Attendance", mr: "उपस्थिती", icon: ClipboardCheck, module: "attendance" },
    { href: "/faculty-portal/classes", en: "Live Classes", mr: "लाईव्ह वर्ग", icon: MonitorPlay, module: "classes" },
    { href: "/faculty-portal/materials", en: "Study Materials", mr: "अभ्यास साहित्य", icon: BookOpen, module: "materials" },
  ]},
  { en: "Assessment", mr: "मूल्यमापन", items: [
    { href: "/faculty-portal/tests", en: "Tests", mr: "चाचण्या", icon: PenSquare, module: "exams" },
    { href: "/faculty-portal/questions", en: "Question Bank", mr: "प्रश्नपेढी", icon: Database, module: "questionBank" },
    { href: "/faculty-portal/assignments", en: "Assignments", mr: "गृहपाठ", icon: FileText, module: "assignments" },
    { href: "/faculty-portal/results", en: "Results", mr: "निकाल", icon: Trophy, module: "results" },
    { href: "/faculty-portal/notifications", en: "Notifications", mr: "सूचना", icon: Bell },
  ]},
];
export default function FacultyLayout({ children }: { children: React.ReactNode }) {
  return <PortalShell roles={["faculty"]} groups={groups} title={{ en: "Faculty portal", mr: "प्राध्यापक पोर्टल" }}>{children}</PortalShell>;
}
