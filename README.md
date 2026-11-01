# Vidyadhan Academy | विद्याधन अकॅडमी

Public website + clickable **student, faculty and admin portal prototype** for a competitive-exam coaching institute in Maharashtra.
Built with **Next.js 15 + TypeScript + Tailwind CSS 4**, Marathi-first with an English toggle.

> ⚠️ This is a **front-end prototype**. All names, fees, results, schedules and records are **demo data** (clearly marked in the UI). There is no real backend, SMS, WhatsApp or payment processing yet.

## Run it

Requires Node.js 20+.

```bash
npm install
npm run dev          # http://localhost:3000
# or a production build
npm run build && npm start
```

Other scripts: `npm run typecheck`.

### Demo logins
Open **/login** and use the one-click demo accounts on the right (Student, Library-only member, Faculty, Admin staff, Owner).
Or log in by mobile: any seeded number + any 4-character password, or OTP **`123456`**.
New students can self-register with OTP `123456` at **/login → New student** or during **/admission**.

All data lives in your browser's localStorage. **Admin → Settings → Reset demo data** (owner) restores the sample data.

## Put in the real academy details

Everything that must not be invented is a placeholder in **one file**: [`src/config/academy.ts`](src/config/academy.ts)
(address, phone, WhatsApp number, email, office hours, map embed, logo, GSTIN, social links).

Other content is editable from **Admin → Website CMS** (hero text, why-us, about, announcements, FAQs, faculty profiles, SEO) and **Admin → Courses / Batches / Library → Plans** (fees). For permanent defaults, edit [`src/lib/seed.ts`](src/lib/seed.ts).

## Google reviews on the website

The site shows **genuine Google reviews** through the official Google Places API (no scraping), via the server route `src/app/api/google-reviews/route.ts`. It's shown on the Home, Results and Testimonials pages and in Admin → Results & Achievements.

1. Google Cloud Console → create a project → enable billing → enable **Places API (New)**.
2. Create an API key and restrict it to Places API (New).
3. Find the academy's **Place ID** with Google's [Place ID Finder](https://developers.google.com/maps/documentation/places/web-service/place-id).
4. Vercel → Project → Settings → Environment Variables → add `GOOGLE_PLACES_API_KEY` and `GOOGLE_PLACE_ID` → **Redeploy**.
5. `googleMapsUrl` in `src/config/academy.ts` is already set to the academy's Google listing (https://share.google/VwVBzYt5DnkekqyiU) for the "See reviews on Google" and "Get directions" buttons.

Google returns **up to 5 reviews** (selected by Google), refreshed every 6 hours, with the required author attribution. Showing *all* reviews would need the Google Business Profile API (owner sign-in), which needs the real backend.

## Successful-student photos

Real results live in `src/lib/seed.ts` (`achievements`) with images in `public/results/`. To add one permanently: put the image in `public/results/`, add an entry, commit, and Vercel redeploys. Admin → Results & Achievements also lets you upload photos and posters, but in this prototype they're saved only in that browser until the backend and cloud storage exist.

## What's in the prototype

| Area | Routes | Highlights |
|---|---|---|
| Public website | `/`, `/about`, `/courses`, `/courses/[slug]`, `/syllabus`, `/fees`, `/batches`, `/faculty`, `/results`, `/testimonials`, `/gallery`, `/announcements`, `/exam-updates`, `/resources`, `/library`, `/contact`, `/enquiry`, `/admission`, `/login`, `/verify` | Marathi/English, SEO metadata per page, quick + detailed enquiry with duplicate detection, 5-step online admission (OTP → batch → promo/instalments → payment → receipt), WhatsApp CTA, certificate/ID QR verification |
| Student portal | `/student/...` | Dashboard, course content hierarchy, live classes (join + attendance), recorded lectures (resume playback, data-saver), materials (view/download rules, size shown, watermark), **exam engine**, results & rankings, assignments (multi-file upload, resubmit, late rules), attendance (calendar + QR scan), fees & receipts, library membership & renewal, exam updates (follow/bookmark), calendar, notifications, support tickets + FAQ, digital ID card, profile (content language, consent, data export) |
| Faculty portal | `/faculty-portal/...` | Only assigned batches + permitted modules: attendance (manual + rotating QR), live/recorded classes, material upload (→ approval), tests, question bank, assignments & grading, results (if granted), batch notes |
| Admin portal | `/admin/...` | Dashboard, enquiries CRM, students, admissions/enrolments/batch transfers, courses, syllabus (versioned, CSV import with row errors), batches, branches/classrooms, classes, material approvals, question bank (bilingual, CSV import + duplicate check), exams, results, assignments, attendance (corrections need reason), fees/payments/refunds/offers, receipts (reprints logged), **library module** (memberships, check-in desk, seat map, plans), official exam updates review queue + sources, notifications (channels, audiences, schedule, templates), tickets, achievements (consent-gated), referrals (disabled by default), ID cards & certificates, reports (filters, YoY, CSV/Excel/PDF), CMS, **per-user permission matrix**, settings (gateways, GST, thresholds), audit log |

### Exam engine
Countdown timer, palette (answered / not answered / marked for review), autosave on every answer, resume after reload or connection loss (time keeps running), auto-submit at time-out, duplicate-submission guard, tab-switch tracking (an indicator only, not a guarantee against cheating), and in-test Marathi ↔ English switching without changing the answer key. Results can be immediate or published by the admin, with batch-wise and academy-wide rank, subject-wise analysis and an explanation review.

### Roles & permissions
Owner = full access. Each staff/faculty account has *No access / View only / View & manage* per module. Faculty are also limited to their assigned batches. Granting finance, admission or system modules to faculty asks for confirmation. Permission changes and important financial and admin actions are written to the audit log.

## What's real vs simulated

| Works in the prototype | Simulated | Needs backend / third-party integration |
|---|---|---|
| Navigation, forms, validation, filters, all CRUD, status workflows, permission enforcement in the UI, fee/discount/instalment calculations, receipts (print → PDF), ranks, reports, CSV exports/imports, QR codes, bilingual UI/content | OTP (fixed `123456`), payments (success/failure buttons), message delivery counts, video playback, file uploads (names/sizes only), QR camera scan (code entry), rate limits (client-side) | Database & REST API, real auth/sessions, SMS/WhatsApp (BSP + approved templates)/email/Telegram providers, payment gateways + webhooks, S3 storage, secure video streaming, exam-update fetching (RSS / official sites / X API needs paid access), server-side permission checks, backups, PDF generation service |

## Suggested production architecture
Next.js front end → modular **NestJS** REST API (one backend for web + future Android/iOS) → **PostgreSQL**, with S3-compatible storage, a queue for notifications and reminders, and replaceable provider adapters (SMS, WhatsApp, payments, video). Use an India hosting region, separate dev/staging/prod environments, secrets only in environment variables (see `.env.example`), encryption in transit and at rest, daily encrypted off-site backups with restore tests, and a DPDP Act legal review.

The data model in [`src/lib/types.ts`](src/lib/types.ts) already carries `branchId` on relevant records, plus separate payment, admission and enrolment statuses, so multiple branches can be added later.

## Recommended phasing
- **MVP:** public site + CMS, registration/login, students, courses/batches, enquiries, admissions, fees & receipts, basic materials, tests/results, attendance, library memberships, in-app/SMS notifications, essential reports.
- **Phase 2:** assignments, advanced question-bank imports, live-class integrations, exam-update automation, support tickets, ID cards/certificates, advanced analytics.
- **Phase 3:** referral campaigns, multi-branch operations, deeper integrations and automation, native mobile apps.

## Open decisions / assumptions
- Real fees, address, contact numbers, logo, faculty names and genuine results: **to be supplied by the academy**.
- Payment gateway(s), SMS/WhatsApp BSP, email and video providers: to be chosen.
- GST applicability and rates need professional validation (GST is off by default).
- Library seat count is assumed to be 80 (`SEATS` in `src/views/admin2.tsx`).
- Discounts above ₹5,000 assumed to need owner approval (`APPROVAL_LIMIT` in `src/views/admin.tsx`).

## Project structure
```
src/
  app/(public)/…        public pages (thin server pages with SEO metadata)
  app/student|faculty-portal|admin/[[...slug]]  portal routes
  components/           UI kit, shells, receipt, brand
  config/academy.ts     ← real academy details go here
  lib/                  types, seed (demo data), store, domain actions, i18n, utils
  views/                page implementations (public, auth, student, exam, faculty, admin, admin2, ops)
```
