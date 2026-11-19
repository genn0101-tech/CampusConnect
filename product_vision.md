# Campus Connect — Product Vision

**Author:** Gen Nakamura
**Institution:** Green River College
**Project Status:** Phase 2 (Data Structuring) — In Progress
**Last Updated:** April 2026

---

## 1. Problem Statement

Have you ever hosted a student event where you had to check in every single participant by yourself? Last quarter, I hosted the GRIT Basketball Tournament, and almost 60 people showed up. Around 30 of them were registered players, and another 30 or so came as spectators. As the club founder, I was the one standing at the entrance, asking each player their name one by one to confirm who actually came. That took a lot of time, and I was also busy running the event itself.

For the spectators, it was even worse. We had no way to record them at all. They just walked in and walked out, and we had no idea how many came or who they were. Personally, I felt frustrated because this data is important. When our club applies for funding, the school asks us to prove how many students we are engaging with. But paper sign-in sheets cannot really show that, and Microsoft Forms only captures the entrance, not the whole event.

This is not only a GRIT problem. Whenever I attend other campus events as a student, I also have to fill out paper forms at the entrance. It is slow for me as a participant, and I’m sure it’s even harder for the organizers on the other side. The real issue is that attendance data is either lost or stuck on paper, and we cannot use it later for anything useful like reports, feedback, or funding.

---

## 2. Target Users

**Primary User**
- GRIT operator (myself). I am the first person who will use this, and GRIT is the first real environment to test it.

**Secondary Users**
- GRIT members and Japanese Club members. These are the communities I can reach directly, so they are the best early testers.

**Long-term Users**
- Other student organizations and campus event operators at Green River College. Once the tool works well in two clubs, it should be easier to expand from there.

---

## 3. User Roles

In Phase 3, I decided to keep the roles simple. Three roles are enough to run a club, and adding too many roles would make the system complicated without clear benefit. If more roles are needed later, I can add them in a future phase.

| Role | What they can do |
|------|------------------|
| **Owner** | Create events, manage invitation lists, grant permissions, see all the data |
| **Member** | Check in and check out, view their own attendance history |
| **Guest** | Check in and check out only |

---

## 4. Core Features

### Phase 3 — Must have (UC application, November 2026)

1. Event creation by Owner
2. Invitation list for existing Members
3. Guest registration with StudentID and Email
4. Check-in and check-out through the web, using a QR code
5. Stay duration calculation
6. Attendance rate calculation
7. Funding report generation
8. Automatic reminder emails

### Phase 4 — Must have (UW application, February 2027)

1. Bluetooth-based proximity check-in
2. Minimal group chat for event coordination
3. Automatic promotion from Guest to Member based on repeat attendance

---

## 5. Why Not Just Use Microsoft Forms?

This is a fair question. Some clubs already use Microsoft Forms with a QR code, and it works for simple check-in. Honestly, when I first thought about this project, I also wondered if Forms was already enough. However, when I looked at what I actually need for GRIT, I realized Forms only covers the entrance. It does not cover the whole event.

| Feature | Microsoft Forms | Campus Connect |
|---------|-----------------|----------------|
| QR check-in | Yes | Yes |
| Check-out recording | No | Yes |
| Stay duration | No | Yes |
| Attendance rate across events | No (manual work) | Yes |
| Funding-ready reports | No | Yes |
| Identify returning members automatically | No | Yes |
| Bluetooth proximity check-in | No | Yes (Phase 4) |

Specifically, what I want is not just "who came in." I want to know how long people stayed, how often they come back, and whether we can show this data to the school for funding. Microsoft Forms cannot do that, at least not without a lot of extra manual work.

---

## 6. Technical Concerns

There are a few open problems I’m still thinking about.

**Bluetooth host device**
Proximity-based check-in needs one device to stay at the venue the whole time. If the Owner has to hold it, they cannot leave, which is not realistic during a busy event. I’m thinking about using a club-shared tablet, or letting officers handle it. I will decide this in Phase 4.

**iOS and Android differences**
Bluetooth works a bit differently on iPhone and Android. Because of that, I don’t want Bluetooth to be the only way to check in. The web version should always work as a backup for anyone.

**Don’t force people to install an app**
Most people at events are first-timers, and asking them to install an app at the door is not realistic. So the Guest flow has to work from a browser, with the minimum information needed (StudentID and Email).

---

## 7. Out of Scope

To keep the project focused, I decided these are not part of the plan:

- General messaging or social features beyond what the event needs
- Payment or ticketing
- Big analytics dashboards beyond the funding report
- Use at other colleges before it actually works at Green River College

I might add some of these later, but not now.

---

## 8. Success Metrics

I don’t want to measure success by how many features I built. I want to measure it by what actually happens when I use it at GRIT.

**Phase 3 success:**
- Used in at least 3 GRIT events
- At least 30 participants registered in the system
- Check-in time is clearly faster than the paper method
- At least one funding application is submitted using a Campus Connect report

**Phase 4 success:**
- Bluetooth check-in works in at least 1 real event
- Guest-to-Member auto promotion works for people who come back
- At least one more student organization starts using it

---

## 9. Development Roadmap

| Phase | Focus | Target |
|-------|-------|--------|
| Phase 1 | Local CSV data processing engine | Completed |
| Phase 2 | Multi-CSV structured data | Spring 2026 |
| Phase 3 | Web application with Spring Boot | Summer 2026 |
| Phase 4 | Reminders, analytics, Bluetooth/NFC | Fall 2026 – Winter 2027 |

---

## 10. Why I Am Building This

I’m not building Campus Connect to show off technology. I’m building it because I actually had this problem when I was running GRIT, and I didn’t want to have it again.

What I want to achieve is three things:

1. Replace paper sign-in sheets with real data that we can use later.
2. Give student organizations a way to prove their activities when they apply for funding.
3. Build something that starts with one club (GRIT), and can grow into a tool the whole Green River College community can use.

I believe good engineering starts from real problems, not from cool technology looking for a place to be used. That’s the reason I started this project, and that’s the reason I want to keep building it until it actually works for my own club first.
