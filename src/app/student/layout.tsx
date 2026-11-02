"use client";
import { Bell, Bookmark, BookOpen, CalendarDays, ClipboardCheck, CreditCard, FileText, GraduationCap, HelpCircle, IdCard, LayoutDashboard, Library, MonitorPlay, Newspaper, PenSquare, PlayCircle, Trophy, User } from "lucide-react";
import { PortalShell, type NavGroup } from "@/components/PortalShell";

const groups: NavGroup[] = [
  { en: "Learn", mr: "अभ्यास", items: [
    { href: "/student", en: "Dashboard", mr: "डॅशबोर्ड", icon: LayoutDashboard },
    { href: "/student/courses", en: "My Courses", mr: "माझे अभ्यासक्रम", icon: GraduationCap },
    { href: "/student/live", en: "Live Classes", mr: "लाईव्ह वर्ग", icon: MonitorPlay },
    { href: "/student/recorded", en: "Recorded Lectures", mr: "रेकॉर्डेड व्याख्याने", icon: PlayCircle },
    { href: "/student/materials", en: "Study Materials", mr: "अभ्यास साहित्य", icon: BookOpen },
  ]},
  { en: "Practice", mr: "सराव", items: [
    { href: "/student/tests", en: "Tests", mr: "चाचण्या", icon: PenSquare },
    { href: "/student/results", en: "Results & Ranking", mr: "निकाल व क्रमांक", icon: Trophy },
    { href: "/student/assignments", en: "Assignments", mr: "गृहपाठ", icon: FileText },
    { href: "/student/attendance", en: "Attendance", mr: "उपस्थिती", icon: ClipboardCheck },
  ]},
  { en: "Account", mr: "खाते", items: [
    { href: "/student/fees", en: "Fees & Receipts", mr: "शुल्क व पावत्या", icon: CreditCard },
    { href: "/student/library", en: "Abhyasika Kendra Membership", mr: "अभ्यासिका केंद्र सदस्यत्व", icon: Library },
    { href: "/student/updates", en: "Official Exam Updates", mr: "अधिकृत परीक्षा अपडेट्स", icon: Newspaper },
    { href: "/student/calendar", en: "Calendar", mr: "दिनदर्शिका", icon: CalendarDays },
    { href: "/student/bookmarks", en: "Bookmarks", mr: "बुकमार्क्स", icon: Bookmark },
    { href: "/student/notifications", en: "Notifications", mr: "सूचना", icon: Bell },
    { href: "/student/support", en: "Support", mr: "मदत", icon: HelpCircle },
    { href: "/student/id-card", en: "ID Card", mr: "ओळखपत्र", icon: IdCard },
    { href: "/student/profile", en: "Profile", mr: "प्रोफाइल", icon: User },
  ]},
];

export default function StudentLayout({ children }: { children: React.ReactNode }) {
  return <PortalShell roles={["student"]} groups={groups} title={{ en: "Student portal", mr: "विद्यार्थी पोर्टल" }} notifHref="/student/notifications">{children}</PortalShell>;
}
