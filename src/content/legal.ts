import type { L } from "@/lib/types";

/**
 * DRAFT legal text for Vidyadhan Academy. Not reviewed by a lawyer.
 * Text inside [[double square brackets]] is a blank the academy must fill or confirm;
 * the site shows it highlighted so nothing is missed before publishing.
 */
export type Block = { p: L } | { ul: L[] };
export interface LegalSection { id: string; title: L; body: Block[] }
export interface LegalDoc { title: L; intro: L; updated: L; sections: LegalSection[] }

const L = (en: string, mr: string): L => ({ en, mr });
const p = (en: string, mr: string): Block => ({ p: L(en, mr) });
const ul = (...items: [string, string][]): Block => ({ ul: items.map(([en, mr]) => L(en, mr)) });

export const PRIVACY: LegalDoc = {
  title: L("Privacy Policy", "गोपनीयता धोरण"),
  intro: L(
    "How Vidyadhan Academy collects, uses, shares and protects your personal information.",
    "विद्याधन अकॅडमी आपली वैयक्तिक माहिती कशी गोळा करते, वापरते, शेअर करते व सुरक्षित ठेवते.",
  ),
  updated: L("Draft version 0.1 · [[date to be set when published]]", "मसुदा आवृत्ती ०.१ · [[प्रसिद्धीच्या वेळी तारीख टाका]]"),
  sections: [
    {
      id: "who", title: L("Who we are", "आम्ही कोण आहोत"),
      body: [
        p("Vidyadhan Academy, Hadapsar, Pune, Maharashtra (\"the Academy\", \"we\") runs coaching for MPSC, PSI, STI, ASO and related examinations, an online learning portal and an Abhyasika Kendra (self-study centre). For the personal data described in this policy, the Academy decides why and how the data is used. Under the Digital Personal Data Protection Act, 2023 this makes the Academy a \"Data Fiduciary\".",
          "विद्याधन अकॅडमी, हडपसर, पुणे, महाराष्ट्र (\"अकॅडमी\", \"आम्ही\") एमपीएससी, पीएसआय, एसटीआय, एएसओ व संबंधित परीक्षांचे कोचिंग, ऑनलाइन शिक्षण पोर्टल आणि अभ्यासिका केंद्र चालवते. या धोरणातील वैयक्तिक माहिती कशासाठी व कशी वापरायची हे अकॅडमी ठरवते. डिजिटल वैयक्तिक माहिती संरक्षण कायदा, 2023 नुसार अकॅडमी 'डेटा फिड्युशियरी' आहे."),
        p("Our contact details are on the Contact page of this website.", "आमचे संपर्क तपशील या संकेतस्थळाच्या 'संपर्क' पानावर आहेत."),
      ],
    },
    {
      id: "collect", title: L("What information we collect", "आम्ही कोणती माहिती गोळा करतो"),
      body: [
        p("We collect only what we need for the purposes below. Depending on how you use the Academy, this may include:", "खालील उद्देशांसाठी आवश्यक तेवढीच माहिती आम्ही गोळा करतो. आपण अकॅडमीच्या सेवा कशा वापरता यावर अवलंबून यात खालील माहिती असू शकते:"),
        ul(
          ["Enquiry details: name, mobile number, course of interest and your message.", "चौकशीचे तपशील: नाव, मोबाईल क्रमांक, इच्छित अभ्यासक्रम व आपला संदेश."],
          ["Admission details: full name, date of birth, gender, parents' or guardian's names and mobile numbers, email, passport-size photo, permanent and current address, highest qualification, and how you heard about us.", "प्रवेशाचे तपशील: पूर्ण नाव, जन्मतारीख, लिंग, पालकांची नावे व मोबाईल, ईमेल, पासपोर्ट आकाराचा फोटो, कायमचा व सध्याचा पत्ता, सर्वोच्च शैक्षणिक पात्रता व आमच्याबद्दल कसे समजले."],
          ["Optional details: Aadhaar number and a copy of the Aadhaar card, and your category (for example Open, OBC, SC, ST). You can choose not to give these unless the Academy tells you at admission that a particular item is required.", "ऐच्छिक माहिती: आधार क्रमांक व आधार कार्डची प्रत, आणि आपला प्रवर्ग (उदा. खुला, ओबीसी, अनुसूचित जाती/जमाती). प्रवेशाच्या वेळी अकॅडमीने एखादी गोष्ट आवश्यक असल्याचे सांगितल्याशिवाय ही माहिती देणे सक्तीचे नाही."],
          ["Fee records: amounts, instalments, payment mode and receipts. Card, UPI and bank details are handled by the payment provider; we do not store them.", "शुल्काच्या नोंदी: रक्कम, हप्ते, पेमेंट पद्धत व पावत्या. कार्ड, यूपीआय व बँक तपशील पेमेंट कंपनी हाताळते; ते आम्ही साठवत नाही."],
          ["Learning records: batches, attendance, test scores, ranks, assignment submissions and activity in live and recorded classes.", "शैक्षणिक नोंदी: बॅच, उपस्थिती, चाचणी गुण, क्रमांक, गृहपाठ सादरीकरण व लाईव्ह/रेकॉर्डेड वर्गांतील सहभाग."],
          ["Abhyasika Kendra records: membership plan, seat and locker, and check-in and check-out times.", "अभ्यासिका केंद्र नोंदी: सदस्यत्व योजना, आसन व लॉकर, आणि प्रवेश/निर्गमन वेळा."],
          ["Support and communication: help tickets, messages you send us, and your message preferences.", "मदत व संवाद: मदत तक्रारी, आपण पाठवलेले संदेश व आपल्या संदेश-पसंती."],
          ["Technical data: your chosen language, login session and basic device and error information.", "तांत्रिक माहिती: आपण निवडलेली भाषा, लॉगिन सत्र आणि मूलभूत उपकरण व त्रुटी माहिती."],
        ),
      ],
    },
    {
      id: "use", title: L("How we use your information", "आम्ही आपली माहिती कशी वापरतो"),
      body: [
        ul(
          ["To process your enquiry, admission and enrolment.", "आपली चौकशी, प्रवेश व नोंदणी पूर्ण करण्यासाठी."],
          ["To run classes, tests, attendance and results, and to give you study material and recordings.", "वर्ग, चाचण्या, उपस्थिती व निकाल चालवण्यासाठी आणि अभ्यास साहित्य व रेकॉर्डिंग देण्यासाठी."],
          ["To collect fees, issue receipts and keep accounts.", "शुल्क घेण्यासाठी, पावत्या देण्यासाठी व हिशेब ठेवण्यासाठी."],
          ["To manage Abhyasika Kendra memberships, seats and entry.", "अभ्यासिका केंद्राचे सदस्यत्व, आसन व प्रवेश व्यवस्थापित करण्यासाठी."],
          ["To send you class, fee, attendance, result and exam-update messages.", "वर्ग, शुल्क, उपस्थिती, निकाल व परीक्षा अपडेट्सचे संदेश पाठवण्यासाठी."],
          ["To answer your questions and resolve problems.", "आपले प्रश्न सोडवण्यासाठी व अडचणी दूर करण्यासाठी."],
          ["To keep records the law requires and to protect the Academy's rights.", "कायद्याने आवश्यक नोंदी ठेवण्यासाठी व अकॅडमीचे हक्क जपण्यासाठी."],
          ["To understand, in aggregate, how our services are used so we can improve them. We do not build profiles to target you with advertising.", "सेवा सुधारण्यासाठी त्यांचा एकत्रित वापर समजून घेण्यासाठी. जाहिरातींसाठी आम्ही आपली प्रोफाइल तयार करत नाही."],
        ),
        p("We use your information only for these purposes, and for another purpose only after asking you again where the law requires it.", "आम्ही आपली माहिती फक्त या उद्देशांसाठी वापरतो; कायद्याने आवश्यक असेल तेथे इतर उद्देशासाठी वापरण्यापूर्वी आपली पुन्हा संमती घेतो."),
      ],
    },
    {
      id: "aadhaar", title: L("Aadhaar and your photo", "आधार व आपला फोटो"),
      body: [
        ul(
          ["Giving your Aadhaar number is optional unless the Academy states otherwise at admission and the law allows it.", "आपला आधार क्रमांक देणे ऐच्छिक आहे; प्रवेशाच्या वेळी अकॅडमीने वेगळे सांगितले व कायद्याने परवानगी असेल तरच तो सक्तीचा."],
          ["Staff see the number only in masked form (for example XXXX XXXX 1234). Only authorised staff can choose to view the full number, and each time they do, it is recorded in an audit log.", "कर्मचाऱ्यांना क्रमांक फक्त झाकलेल्या स्वरूपात (उदा. XXXX XXXX 1234) दिसतो. फक्त अधिकृत कर्मचारी पूर्ण क्रमांक पाहू शकतात आणि प्रत्येक वेळी त्याची ऑडिट लॉगमध्ये नोंद होते."],
          ["We use your Aadhaar only to identify you for admission and records. We do not use it for marketing.", "आधारचा वापर आम्ही फक्त प्रवेश व नोंदीसाठी ओळख पटवण्यासाठी करतो; जाहिरातीसाठी करत नाही."],
          ["Your passport-size photo is used for your student record and ID card.", "आपला पासपोर्ट आकाराचा फोटो विद्यार्थी नोंद व ओळखपत्रासाठी वापरला जातो."],
        ),
      ],
    },
    {
      id: "publish", title: L("Publishing results and photos", "निकाल व फोटो प्रसिद्ध करणे"),
      body: [
        p("We publish a student's name, photo or result on our website, social media or posters only with that student's consent (and a parent's or guardian's consent for a student under 18). You can withdraw this consent at any time and we will remove the item from our website and stop using it in new material.",
          "विद्यार्थ्याचे नाव, फोटो किंवा निकाल आम्ही संकेतस्थळावर, समाजमाध्यमांवर किंवा पोस्टरवर फक्त त्या विद्यार्थ्याच्या संमतीनेच (१८ वर्षांखालील असल्यास पालकांच्या संमतीसह) प्रसिद्ध करतो. ही संमती आपण केव्हाही मागे घेऊ शकता; आम्ही ते संकेतस्थळावरून काढून टाकतो व नवीन साहित्यात वापरणे थांबवतो."),
      ],
    },
    {
      id: "share", title: L("Who we share information with", "आम्ही माहिती कोणाशी शेअर करतो"),
      body: [
        p("We do not sell your personal information. We share it only as needed with:", "आम्ही आपली वैयक्तिक माहिती विकत नाही. आवश्यकतेनुसार फक्त खालील घटकांशी शेअर करतो:"),
        ul(
          ["Service providers who help us run the Academy, such as website hosting and database providers, payment gateways, SMS, WhatsApp, Telegram and email providers, and live-class or video providers. They may use your data only to provide their service to us. [[Academy to list the providers actually used before publishing.]]", "अकॅडमी चालवण्यात मदत करणारे सेवा पुरवठादार, जसे की वेबसाइट होस्टिंग व डेटाबेस, पेमेंट गेटवे, एसएमएस, व्हॉट्सअ‍ॅप, टेलिग्राम व ईमेल, आणि लाईव्ह-क्लास/व्हिडिओ सेवा. ते आपली माहिती फक्त आम्हाला सेवा देण्यासाठी वापरू शकतात. [[प्रसिद्धीपूर्वी प्रत्यक्ष वापरलेल्या पुरवठादारांची यादी द्या.]]"],
          ["Google, when you view our map or Google reviews. Their content is loaded from Google and is covered by Google's own privacy policy.", "आमचा नकाशा किंवा गुगल पुनरावलोकने पाहताना गुगल. ती माहिती गुगलकडून येते व गुगलच्या स्वतःच्या गोपनीयता धोरणाखाली असते."],
          ["Courts, police or government authorities when the law requires it or to protect rights and safety.", "कायद्याने आवश्यक असल्यास किंवा हक्क व सुरक्षितता जपण्यासाठी न्यायालये, पोलीस किंवा सरकारी यंत्रणा."],
        ),
      ],
    },
    {
      id: "messages", title: L("Messages we send you", "आम्ही पाठवत असलेले संदेश"),
      body: [
        p("We send service messages about your classes, fees, attendance, results and exam updates by app notification, SMS, WhatsApp, Telegram or email. If you gave a parent's or guardian's number, alerts about fees and attendance may also go to them. We send promotional messages only if you agree, and you can stop them at any time by telling us or using the stop option in the message.",
          "आपल्या वर्गांबद्दल, शुल्क, उपस्थिती, निकाल व परीक्षा अपडेट्सबद्दल आम्ही अ‍ॅप सूचना, एसएमएस, व्हॉट्सअ‍ॅप, टेलिग्राम किंवा ईमेलने सेवा-संदेश पाठवतो. आपण पालकांचा क्रमांक दिला असल्यास शुल्क व उपस्थितीच्या सूचना त्यांनाही जाऊ शकतात. जाहिरातीचे संदेश आम्ही फक्त आपली संमती असल्यास पाठवतो व आपण ते केव्हाही थांबवू शकता."),
      ],
    },
    {
      id: "cookies", title: L("Cookies and browser storage", "कुकीज व ब्राउझर साठवण"),
      body: [
        p("We use your browser's storage only to keep the site working: your language, your login and, where needed, saved form progress. We do not use advertising trackers. [[Update this section if analytics or advertising tools are added later.]]",
          "संकेतस्थळ नीट चालण्यासाठीच आम्ही आपल्या ब्राउझरची साठवण वापरतो: आपली भाषा, आपले लॉगिन आणि गरजेनुसार भरलेला फॉर्म. आम्ही जाहिरात ट्रॅकर वापरत नाही. [[भविष्यात अ‍ॅनालिटिक्स किंवा जाहिरात साधने जोडल्यास हा भाग अद्ययावत करा.]]"),
      ],
    },
    {
      id: "retention", title: L("How long we keep information", "माहिती किती काळ ठेवतो"),
      body: [
        p("We keep your information only as long as it is needed for the purposes in this policy or as the law requires, and then delete or anonymise it. [[Academy to confirm periods, for example: student records for ___ years after the course ends; enquiries for ___ months; fee and receipt records as required by tax law.]]",
          "या धोरणातील उद्देशांसाठी किंवा कायद्याने आवश्यक तोपर्यंतच आम्ही माहिती ठेवतो व नंतर ती नष्ट करतो किंवा ओळख काढून टाकतो. [[अकॅडमीने कालावधी निश्चित करावा, उदा. अभ्यासक्रम संपल्यानंतर विद्यार्थी नोंदी ___ वर्षे; चौकशी ___ महिने; शुल्क व पावत्या कर कायद्यानुसार.]]"),
      ],
    },
    {
      id: "security", title: L("How we protect it", "आम्ही माहिती कशी सुरक्षित ठेवतो"),
      body: [
        ul(
          ["Access is limited by role. Staff and faculty see only what their work needs.", "भूमिकेनुसार प्रवेश मर्यादित असतो. कर्मचारी व प्राध्यापकांना फक्त कामासाठी आवश्यक माहिती दिसते."],
          ["Sensitive items such as Aadhaar are masked, and important actions are recorded in an audit log.", "आधारसारखी संवेदनशील माहिती झाकलेली असते आणि महत्त्वाच्या कृतींची ऑडिट लॉगमध्ये नोंद होते."],
          ["We use secure connections and reputable hosting. [[Confirm encryption of stored data and backup practices before going live.]]", "आम्ही सुरक्षित कनेक्शन व विश्वासार्ह होस्टिंग वापरतो. [[प्रत्यक्ष वापरापूर्वी साठवलेल्या माहितीचे एन्क्रिप्शन व बॅकअप पद्धती निश्चित करा.]]"],
        ),
        p("No system is completely secure. If a personal data breach affects you, we will tell you and the authorities as the law requires.", "कोणतीही यंत्रणा पूर्णपणे सुरक्षित नसते. आपल्या माहितीबाबत उल्लंघन झाल्यास कायद्यानुसार आम्ही आपल्याला व संबंधित यंत्रणांना कळवू."),
      ],
    },
    {
      id: "minors", title: L("Students under 18", "१८ वर्षांखालील विद्यार्थी"),
      body: [
        p("If a student is under 18, a parent or legal guardian must consent to us handling the student's data and must provide it or approve it. We do not track children's behaviour or show them targeted advertising.",
          "विद्यार्थी १८ वर्षांखालील असल्यास त्याची माहिती हाताळण्यास पालक किंवा कायदेशीर संरक्षकाची संमती आवश्यक आहे व माहिती त्यांनी द्यावी किंवा मान्य करावी. आम्ही मुलांच्या वर्तनावर नजर ठेवत नाही व त्यांना लक्ष्यित जाहिराती दाखवत नाही."),
      ],
    },
    {
      id: "rights", title: L("Your rights", "आपले हक्क"),
      body: [
        p("Under the Digital Personal Data Protection Act, 2023 you can:", "डिजिटल वैयक्तिक माहिती संरक्षण कायदा, 2023 नुसार आपण हे करू शकता:"),
        ul(
          ["ask for a summary of the personal data we hold about you and how it is used;", "आमच्याकडे आपली कोणती वैयक्तिक माहिती आहे व ती कशी वापरली जाते याचा सारांश मागू शकता;"],
          ["ask us to correct, complete or update it;", "ती दुरुस्त, पूर्ण किंवा अद्ययावत करण्यास सांगू शकता;"],
          ["ask us to erase it when it is no longer needed and the law does not require us to keep it;", "ती गरज नसल्यास व कायद्याने ठेवणे आवश्यक नसल्यास पुसून टाकण्यास सांगू शकता;"],
          ["withdraw a consent you gave, as easily as you gave it;", "दिलेली संमती ती देताना जितकी सोपी होती तितक्याच सहजतेने मागे घेऊ शकता;"],
          ["nominate someone to exercise these rights for you if you die or cannot do so; and", "आपण मृत्यू पावल्यास किंवा असमर्थ असल्यास हे हक्क वापरण्यासाठी कोणाला तरी नामनिर्देशित करू शकता; आणि"],
          ["complain to us and, if unresolved, to the Data Protection Board of India.", "आमच्याकडे तक्रार करू शकता आणि निराकरण न झाल्यास भारतीय डेटा संरक्षण मंडळाकडे."],
        ),
        p("To use these rights, contact the Grievance Officer below. We aim to reply within [[Academy to confirm: number of days]]. Withdrawing a consent does not affect what we did before you withdrew it, and we may need to keep some records the law requires.",
          "हे हक्क वापरण्यासाठी खालील तक्रार निवारण अधिकाऱ्याशी संपर्क करा. आम्ही [[अकॅडमीने कालावधी निश्चित करावा: दिवसांची संख्या]] आत उत्तर देण्याचा प्रयत्न करतो. संमती मागे घेतल्याने तोपर्यंत केलेल्या कामावर परिणाम होत नाही, व कायद्याने आवश्यक काही नोंदी आम्हाला ठेवाव्या लागू शकतात."),
      ],
    },
    {
      id: "grievance", title: L("Grievance Officer", "तक्रार निवारण अधिकारी"),
      body: [
        p("Name: [[Name of the Grievance Officer]] · Email: [[email]] · Phone: [[phone]] · Address: Vidyadhan Academy, Hadapsar, Pune, Maharashtra · Office hours: [[days and times]]",
          "नाव: [[तक्रार निवारण अधिकाऱ्याचे नाव]] · ईमेल: [[ईमेल]] · फोन: [[फोन]] · पत्ता: विद्याधन अकॅडमी, हडपसर, पुणे, महाराष्ट्र · कार्यालयीन वेळ: [[दिवस व वेळ]]"),
      ],
    },
    {
      id: "changes", title: L("Changes to this policy", "या धोरणातील बदल"),
      body: [
        p("We may update this policy. The date at the top shows the latest version. If a change is important, we will tell you on the website or by message.",
          "आम्ही हे धोरण बदलू शकतो. सर्वात वर दिलेली तारीख ताजी आवृत्ती दर्शवते. बदल महत्त्वाचा असल्यास संकेतस्थळावर किंवा संदेशाद्वारे कळवू."),
      ],
    },
  ],
};

export const TERMS: LegalDoc = {
  title: L("Terms and Conditions", "अटी व शर्ती"),
  intro: L(
    "The rules for joining Vidyadhan Academy, using our website and portal, and using the Abhyasika Kendra.",
    "विद्याधन अकॅडमीत प्रवेश, आमचे संकेतस्थळ व पोर्टल आणि अभ्यासिका केंद्र वापरण्याचे नियम.",
  ),
  updated: L("Draft version 0.1 · [[date to be set when published]]", "मसुदा आवृत्ती ०.१ · [[प्रसिद्धीच्या वेळी तारीख टाका]]"),
  sections: [
    {
      id: "accept", title: L("Accepting these terms", "या अटी मान्य करणे"),
      body: [
        p("By enquiring, registering, taking admission, paying fees or using our website, portal or Abhyasika Kendra, you agree to these terms and to our Privacy Policy. If you are under 18, your parent or legal guardian must agree on your behalf.",
          "चौकशी, नोंदणी, प्रवेश, शुल्क भरणे किंवा आमचे संकेतस्थळ, पोर्टल किंवा अभ्यासिका केंद्र वापरणे म्हणजे आपण या अटी व आमचे गोपनीयता धोरण मान्य करता. आपण १८ वर्षांखालील असल्यास आपल्या वतीने पालक किंवा कायदेशीर संरक्षकाने मान्य करावे."),
      ],
    },
    {
      id: "services", title: L("Our services", "आमच्या सेवा"),
      body: [
        p("The Academy provides classroom and online coaching for MPSC, PSI, STI, ASO and related examinations, study material, tests, recorded and live classes, official exam-update information, and a self-study Abhyasika Kendra. Details of each course, batch, timing and fee are published on the website and may change.",
          "अकॅडमी एमपीएससी, पीएसआय, एसटीआय, एएसओ व संबंधित परीक्षांसाठी वर्ग व ऑनलाइन कोचिंग, अभ्यास साहित्य, चाचण्या, रेकॉर्डेड व लाईव्ह वर्ग, अधिकृत परीक्षा अपडेट्सची माहिती आणि स्वयं-अभ्यासासाठी अभ्यासिका केंद्र देते. प्रत्येक अभ्यासक्रम, बॅच, वेळ व शुल्काचे तपशील संकेतस्थळावर प्रसिद्ध असतात व बदलू शकतात."),
      ],
    },
    {
      id: "account", title: L("Your account", "आपले खाते"),
      body: [
        ul(
          ["Give correct and complete information, and keep it up to date.", "बरोबर व पूर्ण माहिती द्या आणि ती अद्ययावत ठेवा."],
          ["Your account is personal. Do not share your login or OTP with anyone.", "आपले खाते वैयक्तिक आहे. आपले लॉगिन किंवा OTP कोणालाही देऊ नका."],
          ["Tell us at once if you think someone else has used your account.", "आपले खाते दुसऱ्याने वापरल्याचा संशय असल्यास लगेच आम्हाला कळवा."],
        ),
      ],
    },
    {
      id: "fees", title: L("Admission, fees and payment", "प्रवेश, शुल्क व पेमेंट"),
      body: [
        ul(
          ["Admission is confirmed only after the fee (or the first instalment) is received and verified.", "शुल्क (किंवा पहिला हप्ता) मिळून पडताळल्यानंतरच प्रवेश निश्चित होतो."],
          ["Fees and instalment dates are as shown for your batch at admission. Taxes, if applicable, are added as the law requires.", "शुल्क व हप्त्यांच्या तारखा प्रवेशाच्या वेळी आपल्या बॅचसाठी दाखवल्याप्रमाणे असतात. लागू असल्यास कर कायद्यानुसार जोडला जातो."],
          ["Pay only through the Academy's official options (online or at the office) and keep your receipt. A failed online payment does not confirm admission.", "फक्त अकॅडमीच्या अधिकृत पद्धतींनीच (ऑनलाइन किंवा कार्यालयात) पैसे भरा व पावती जपून ठेवा. अयशस्वी ऑनलाइन पेमेंटने प्रवेश निश्चित होत नाही."],
          ["If an instalment is late, the Academy may suspend access to classes or materials until it is paid. [[Academy to confirm late-fee rules, if any.]]", "हप्ता उशिरा भरल्यास तो भरेपर्यंत वर्ग किंवा साहित्याचा प्रवेश अकॅडमी थांबवू शकते. [[विलंब शुल्क असल्यास त्याचे नियम अकॅडमीने निश्चित करावेत.]]"],
        ),
      ],
    },
    {
      id: "refunds", title: L("Refunds, cancellation and transfers", "परतावा, रद्दीकरण व बदल"),
      body: [
        p("[[Academy to write its refund policy here. Please state clearly: whether fees are refundable; the time limits and deductions, if any (for example before the batch starts, within the first week, after that); how and when refunds are paid; and registration fees.]]",
          "[[अकॅडमीने येथे आपले परतावा धोरण लिहावे. कृपया स्पष्टपणे सांगा: शुल्क परत मिळते का; वेळेच्या मर्यादा व कपात असल्यास (उदा. बॅच सुरू होण्यापूर्वी, पहिल्या आठवड्यात, त्यानंतर); परतावा कसा व केव्हा दिला जाईल; आणि नोंदणी शुल्क.]]"),
        p("Moving to another batch or course is possible only with the Academy's approval and may involve a fee difference. If the Academy cancels a batch, it will offer a transfer or a refund of the fees paid for the period not delivered.",
          "दुसऱ्या बॅच किंवा अभ्यासक्रमात जाणे अकॅडमीच्या मंजुरीनेच शक्य आहे व त्यात शुल्कातील फरक असू शकतो. अकॅडमीने बॅच रद्द केल्यास बदल किंवा न दिलेल्या कालावधीच्या शुल्काचा परतावा देऊ."),
      ],
    },
    {
      id: "schedule", title: L("Schedules and changes", "वेळापत्रक व बदल"),
      body: [
        p("Class timings, faculty, venues and modes (classroom or online) may change for practical reasons. We will inform you in advance wherever possible. Missed classes may not be repeated, but recordings are provided where the batch includes them.",
          "व्यावहारिक कारणांमुळे वर्गांच्या वेळा, प्राध्यापक, ठिकाण व पद्धत (वर्ग किंवा ऑनलाइन) बदलू शकतात. शक्य असेल तेथे आम्ही आधी कळवू. चुकलेले वर्ग पुन्हा घेतले जाणार नाहीत, परंतु बॅचमध्ये रेकॉर्डिंग समाविष्ट असल्यास ते दिले जाते."),
      ],
    },
    {
      id: "conduct", title: L("Attendance and conduct", "उपस्थिती व वर्तन"),
      body: [
        ul(
          ["Attend regularly and on time. The attendance rules of your batch apply.", "नियमित व वेळेवर हजर राहा. आपल्या बॅचचे उपस्थितीचे नियम लागू आहेत."],
          ["Treat staff, faculty and other students with respect. Harassment, cheating, damage to property or disturbing classes is not allowed.", "कर्मचारी, प्राध्यापक व इतर विद्यार्थ्यांशी आदराने वागा. छळ, कॉपी, मालमत्तेचे नुकसान किंवा वर्गात व्यत्यय आणण्यास परवानगी नाही."],
          ["The Academy may warn, suspend or remove a student who breaks these rules. [[Academy to confirm whether fees are refunded in such a case.]]", "या नियमांचे उल्लंघन करणाऱ्या विद्यार्थ्याला अकॅडमी समज, निलंबन किंवा प्रवेश रद्द करू शकते. [[अशा वेळी शुल्क परत मिळेल का, हे अकॅडमीने निश्चित करावे.]]"],
        ),
      ],
    },
    {
      id: "materials", title: L("Study material and recordings", "अभ्यास साहित्य व रेकॉर्डिंग"),
      body: [
        p("Notes, tests, videos, recordings and other material belong to the Academy or its faculty. They are given to you for your own study only. You must not copy, record, share, upload, sell or distribute them, or share your login so that others can use them. Material may carry your name or ID as a watermark. Breaking this rule can lead to removal and legal action.",
          "नोट्स, चाचण्या, व्हिडिओ, रेकॉर्डिंग व इतर साहित्य अकॅडमी किंवा तिच्या प्राध्यापकांच्या मालकीचे आहे. ते फक्त आपल्या स्वतःच्या अभ्यासासाठी दिले जाते. ते कॉपी, रेकॉर्ड, शेअर, अपलोड, विक्री किंवा वितरित करू नका, किंवा इतरांना वापरता यावे म्हणून आपले लॉगिन देऊ नका. साहित्यावर आपले नाव किंवा आयडी वॉटरमार्क म्हणून असू शकतो. हा नियम मोडल्यास प्रवेश रद्द व कायदेशीर कारवाई होऊ शकते."),
      ],
    },
    {
      id: "tests", title: L("Online tests", "ऑनलाइन चाचण्या"),
      body: [
        p("Attempt tests honestly and on your own. The system may record activity such as leaving the test window, and this is only an indicator, not proof of cheating. The Academy may review an attempt and decide on the result. Scores and ranks are for guidance only.",
          "चाचण्या प्रामाणिकपणे व स्वतः द्या. चाचणी विंडो सोडणे यासारख्या हालचाली प्रणाली नोंदवू शकते; हे फक्त संकेत आहे, कॉपीचा पुरावा नाही. अकॅडमी प्रयत्न तपासून निकालाचा निर्णय घेऊ शकते. गुण व क्रमांक फक्त मार्गदर्शनासाठी आहेत."),
      ],
    },
    {
      id: "abhyasika", title: L("Abhyasika Kendra", "अभ्यासिका केंद्र"),
      body: [
        ul(
          ["Membership gives you a seat (and a locker or a spacious seat if your plan includes it) for the period you paid for. Seats are allotted by the Academy and are not transferable.", "सदस्यत्वात आपण भरलेल्या कालावधीसाठी आसन (आणि योजनेत असल्यास लॉकर किंवा प्रशस्त आसन) मिळते. आसन अकॅडमी देते व ते हस्तांतरणीय नाही."],
          ["A one-time registration fee may apply for a new membership and is not charged again on renewal.", "नवीन सदस्यत्वासाठी एकदाचे नोंदणी शुल्क लागू असू शकते व नूतनीकरणाच्या वेळी पुन्हा आकारले जात नाही."],
          ["Keep silence, follow the timings, and look after your belongings. [[Academy to add timings, locker rules and what happens to belongings left behind.]]", "शांतता राखा, वेळा पाळा व आपल्या वस्तूंची काळजी घ्या. [[अकॅडमीने वेळा, लॉकर नियम व मागे राहिलेल्या वस्तूंबाबतचे नियम जोडावेत.]]"],
          ["The Academy may suspend a membership for breaking the rules.", "नियम मोडल्यास अकॅडमी सदस्यत्व निलंबित करू शकते."],
        ),
      ],
    },
    {
      id: "results", title: L("No guarantee of results", "निकालाची हमी नाही"),
      body: [
        p("We give honest guidance and a structured programme, but success in an examination depends on many factors, including your own effort. The Academy does not guarantee any selection, rank or result. Any past results shown are of individual students and are not a promise for others.",
          "आम्ही प्रामाणिक मार्गदर्शन व नियोजित कार्यक्रम देतो, परंतु परीक्षेतील यश आपल्या स्वतःच्या मेहनतीसह अनेक गोष्टींवर अवलंबून असते. कोणत्याही निवडीची, क्रमांकाची किंवा निकालाची हमी अकॅडमी देत नाही. दाखवलेले मागील निकाल वैयक्तिक विद्यार्थ्यांचे आहेत व ते इतरांसाठी आश्वासन नाही."),
      ],
    },
    {
      id: "updates", title: L("Exam updates and third-party information", "परीक्षा अपडेट्स व तिसऱ्या पक्षाची माहिती"),
      body: [
        p("Exam notices, dates and links on this website are shared for your convenience. They may be incomplete or out of date. Always check the official website or notice of the conducting authority (for example MPSC) before acting. We are not responsible for third-party websites.",
          "या संकेतस्थळावरील परीक्षा सूचना, तारखा व दुवे आपल्या सोयीसाठी दिले आहेत. ते अपूर्ण किंवा जुने असू शकतात. कृती करण्यापूर्वी संबंधित परीक्षा संस्थेचे (उदा. एमपीएससी) अधिकृत संकेतस्थळ किंवा सूचना नेहमी तपासा. तिसऱ्या पक्षाच्या संकेतस्थळांसाठी आम्ही जबाबदार नाही."),
      ],
    },
    {
      id: "website", title: L("Using the website", "संकेतस्थळाचा वापर"),
      body: [
        p("Do not misuse the website or portal: do not try to break in, overload it, collect data from it by automated means, or post unlawful, abusive or false content. The design, text, logo and other content of the website belong to the Academy and may not be copied without permission.",
          "संकेतस्थळ किंवा पोर्टलचा गैरवापर करू नका: त्यात घुसखोरी, जास्त भार टाकणे, स्वयंचलित पद्धतीने माहिती गोळा करणे किंवा बेकायदेशीर, अपमानास्पद वा खोटा मजकूर टाकणे करू नका. संकेतस्थळाची रचना, मजकूर, लोगो व इतर मजकूर अकॅडमीच्या मालकीचा आहे व परवानगीशिवाय कॉपी करू नये."),
      ],
    },
    {
      id: "liability", title: L("Limits of responsibility", "जबाबदारीच्या मर्यादा"),
      body: [
        p("To the extent the law allows, the Academy is not liable for indirect losses, for interruptions beyond its control (such as power, internet or platform failures), or for loss of personal belongings. Nothing here limits any right you have under the Consumer Protection Act, 2019 or other law that cannot be limited.",
          "कायद्याने परवानगी असेल तेवढ्या मर्यादेत, अप्रत्यक्ष नुकसान, आपल्या नियंत्रणाबाहेरचे व्यत्यय (उदा. वीज, इंटरनेट किंवा प्लॅटफॉर्म बिघाड) किंवा वैयक्तिक वस्तूंच्या नुकसानीसाठी अकॅडमी जबाबदार नाही. ग्राहक संरक्षण कायदा, 2019 किंवा इतर कायद्यानुसार आपले जे हक्क मर्यादित करता येत नाहीत त्यावर येथे काहीही परिणाम होत नाही."),
      ],
    },
    {
      id: "law", title: L("Governing law and disputes", "लागू कायदा व वाद"),
      body: [
        p("These terms are governed by the laws of India. We will first try to resolve any problem by talking to you. [[Confirm with a lawyer: courts at Pune to have jurisdiction, and any arbitration clause the Academy wants.]]",
          "या अटींना भारतीय कायदे लागू आहेत. कोणतीही अडचण आम्ही प्रथम आपल्याशी चर्चा करून सोडवण्याचा प्रयत्न करू. [[वकिलाशी निश्चित करा: पुणे येथील न्यायालयांचे अधिकारक्षेत्र व अकॅडमीला हवा असल्यास लवाद कलम.]]"),
      ],
    },
    {
      id: "changes", title: L("Changes and contact", "बदल व संपर्क"),
      body: [
        p("We may update these terms. The date at the top shows the latest version, and using our services after a change means you accept it. For questions, contact us using the details on the Contact page.",
          "आम्ही या अटी बदलू शकतो. सर्वात वर दिलेली तारीख ताजी आवृत्ती दर्शवते व बदलानंतर सेवा वापरणे म्हणजे आपण तो मान्य करता. प्रश्नांसाठी 'संपर्क' पानावरील तपशिलांनी आमच्याशी संपर्क करा."),
      ],
    },
  ],
};
