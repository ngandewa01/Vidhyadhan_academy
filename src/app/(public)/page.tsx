import type { Metadata } from "next";
import HomeView from "./home-view";
export const metadata: Metadata = {
  title: { absolute: "Vidyadhan Academy | विद्याधन अकॅडमी – MPSC, Police Bharti & Competitive Exam Coaching" },
  description: "Competitive exam coaching in Maharashtra – MPSC Rajyaseva, PSI/STI/ASO, Police Bharti, Talathi, Combined Group B & C, with a self-study library.",
};
export default function Page() { return <HomeView />; }
