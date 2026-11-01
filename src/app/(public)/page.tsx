import type { Metadata } from "next";
import HomeView from "./home-view";
export const metadata: Metadata = {
  title: { absolute: "Vidyadhan Academy, Hadapsar Pune | विद्याधन अकॅडमी – MPSC, PSI, STI, ASO Coaching" },
  description: "Nikhil Rudrawar's Vidyadhan Academy in Hadapsar, Pune – coaching for MPSC Rajyaseva, PSI, STI, ASO, Police Bharti and other Maharashtra exams, with a self-study library.",
};
export default function Page() { return <HomeView />; }
