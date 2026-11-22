# Changelog

## [0.22.1]

Needs server 0.106.5.

- **Cards:** a 0% card also shows the payment that clears it by the offer's end ("$1,867/mo clears
  it by Feb 2028"), beside its planned payment.

## [0.22.0]

Needs server 0.106.3 for the card amounts below.

- **Cards:** each card's amount is what's planned for it: a payment set on the web's Payments
  page first; on a 0% card, the month's debt payment rather than the pace that clears it by the
  offer's end; otherwise what's left on the statement. "To cards" on Today and card reminders use
  the same amounts.
- **Notifications:** a new one, "Amazon sync stopped", for the Amazon extension (off until turned
  on).
- **Setup** says Ledger throughout, and updates come from `thorin29/ledger-app`.

## [0.21.0]

Needs server 0.101 (with an older server, Today and Insights stay as they were).

- **The app is called Ledger.**
- **Today** leads with what's available to spend on the main card's statement (until it closes,
  with about how much a day), then checking and the month's budget left, the statement so far,
  **Needs You** (a bill pending at the bank, items to review, a budget past its limit, checking
  running short) and the next bills, each with Record.
- **Tracker** (was Month): a bill whose payment is already at the bank says "Pending at the bank";
  a bill shows overdue only two days past its due day ("Due yesterday", "Due 2 days ago" before that).
- **Cards** opens with the main card's statement: charged so far, bills still to land, the budget
  left on it and the projected statement.
- **Insights** is Overview (income vs expenses, the outlook in the worst and best case, last
  month, debt) and Budget (the month, each card's statement, and by card), as on the web.
- **More** has Review, Transactions (search, "Needs a category", pending, money in, and a sheet to
  set a category or mark a transfer) and Notifications.
- **Notifications:** 14 kinds, all off until turned on under More → Notifications, plus quiet
  hours (9 PM – 7 AM). One job checks the server about every 30 minutes while any is on. The older
  bill, card, weekly and plan reminders are replaced by these.

## [0.20.0]

Needs server 0.46.

- **Pull down to refresh** on Today, This month, Cards, Insights and Plan: reloads from the server
  and sends anything waiting (it doesn't ask the bank; the bank button on Today does that).
- Where this month's money goes is a donut of money in, with a red outer ring when spending passes it.

## [0.19.0]

Needs server 0.45.

- Charts on Insights, as on the web: where this month's money goes, whether spending is on pace,
  the 0% balance against the end of the offer, and money in against money out by month.

## [0.18.0]

Needs server 0.44.

- **Will checking cover it?** on Insights: whether checking covers every bill and full card payment
  through next month, the lowest point, and what each paycheck has to pay.
- An alert when checking would run short on a coming due date, once per date.
- **Make it a bill** on a charge in the plan's transactions.

## [0.17.0]

Needs server 0.43.

- The spending plan is monthly. The headline on Today and Insights is **safe to spend** on extras this
  month, after every bill and the essentials still to come, or "Over by".
- **How it adds up:** money in, bills (paid, due, overdue), the debt payment, the pool, spent, left
  and essentials still to come. Tap a line to see what's in it.
- Each budget opens to its categories, and each category to its transactions. Extras show their
  room, which is never more than safe to spend.
- **Can we afford it?** with one or more purchases, worked out on the phone.
- Earlier months on Insights show their plan.
- One running-low alert, on the month's total: when safe to spend drops under $200, and again if it
  goes over. The payday alert is gone until cash timing returns.

## [0.16.0]

Needs server 0.42.

- "Envelopes" are now the spending plan, and each line is a budget.
- The headline on Today and Insights is a donut of what's left by kind (essentials, planned,
  flexible, and any overage), with what each covers.
- Payday plan on Insights: the debt payment, bills to hold, what to send each card and whether it
  pays it to zero, and what stays in checking.
- Money in against money out for each recent pay period.
- Notifications: the payday plan on payday morning, and once per pay period when the money left runs
  out.

## [0.15.0]

Needs server 0.41.

- The headline is now everything left across all envelopes (or how far over), with the flexible part
  under it, so it matches the envelopes below.
- "Can I afford it?" is replaced by a summary: what's left and over in essentials, planned and
  flexible, and in total.
- Insights can show the envelopes for this pay period or this month.
- Bills come from what's due before the next payday; Amazon charges count by what was in each order.

## [0.14.0]

Needs server 0.40.

- Spending envelopes. Today shows the flexible money left until payday, and a sentence when an
  envelope is used up. Insights leads with where each paycheck goes, every envelope (tap one for
  its categories), and "Can I afford it?", which shows what a purchase would take from the other
  envelopes. Limits and the envelopes themselves are set on the web.

## [0.13.1]

Needs server 0.39.2.

- Today's balance shows when the bank itself last updated it ("Bank data as of 4:12 AM") and when
  the server last checked.

## [0.13.0]

Needs server 0.39.

- Today: when the bills balance comes from the bank, it shows when the bank was last read and a
  button to read it again, in place of tap to edit.
- Review: repeating charges can be added as bills or dismissed here, not only on the web. Short
  notes explain "Done for you" and what recording a match does.

## [0.12.0]

- "Paying off" on Insights: bills paying off a balance, with what's left and when each ends.
  Needs server 0.37.

## [0.11.0]

Needs server 0.35 for the invite QR.

- Second person setup from a QR code: on the first screen, Scan QR code and scan the invite from
  the web (Settings → Sign-in & people). The app checks the server, asks for a username and
  password of their own, creates the account and signs the phone in. A `budget://join` link
  opens the same way.

## [0.10.0]

Needs server 0.34.

- The budget plan at the top of Insights: where the income goes (one bar, with the shortfall past
  its end), the card budget with each card's bills and spending since its statement closed and
  the spending room left, and category targets.
- The Cards tab shows each card's bills, spending and room left under its row.

## [0.9.0]

Needs server 0.33.

- Spending limit: the most everyday spending (cards and debit together) can be each month and
  keep within the plan, at the top of Insights, with how far over or under the last three months
  ran and what's left this month per week. Today shows this month against it, and the weekly
  summary mentions it.

## [0.8.0]

Needs server 0.32 for the new parts of Insights.

- Insights matches the web: a month picker; everyday spending with the same month last year;
  bills, money in and in minus out; pace with what's left after bills, what's still to come (and
  how much lands on cards), and the last seven days; 0% payoff progress; coming up; suggestions
  that open to each month's actual amounts with a small chart; where it went with the usual and
  last year; what changed; top merchants; charts of everyday spending and in minus out by month;
  and money managed for others.
- Tap a category (or "See the charges" on a suggestion) for its own screen: twelve months, this
  month's merchants against their usual, and the charges.
- Weekly summary (More → Weekly summary): a Sunday evening notification with the week's spending,
  the month's pace, what's left after bills and anything coming up. Off until turned on.
- Notifications show the app's logo in its blue.

## [0.7.0]

- Coming up: the next yearly charge (a membership or renewal) shows on Today a few weeks ahead,
  and Insights lists every one due in the next 45 days. Needs server 0.31.

## [0.6.1]

- Today leads with the bills account balance, then First half remaining and Second half
  remaining with what's still owed, as on the web. "Safe to send today" and the lowest point
  are under More → What can I pay?.
- The Cards tab shows a small copy of each card (its colors, name and last digits) instead of a
  color swatch.

## [0.6.0]

Best with server 0.30; on an older server the new parts show what they can.

- Five tabs: Today, Month, Cards, Insights, More. "What can I pay?" moved under More (Today's
  balance card still opens it).
- Cards tab: the card stack, then each card with what to pay, the due date and its statement or
  payoff plan, and the reminder setting.
- Card payment reminders: around 8 AM, on the due date and optionally 1, 3 or 5 days before.
  A card already paid for the cycle is skipped. Tapping one opens the Cards tab. Off until turned
  on in the Cards tab or under More.
- Insights tab: this month's pace against a usual month, the suggestions (tap one for the
  details and charges behind it), and where the money went.
- Today is lighter: one line for the cards and the top suggestion, each opening its tab.

## [0.5.1]

- Cards can be thrown off either side of the screen. A swipe or quick flick sends the front card
  off the way it was moving, and it eases in behind the others from that side, instead of
  swinging back across to the right.

## [0.5.0]

Needs server 0.28 or later; on an older server these simply don't show.

- Card stack on Today: an All cards summary in front (what to pay this cycle, next due date, and
  the balance split into on statements and new since), then each card in the color picked on the
  web. Swipe the front card either way, or tap it, and it slides to the back.
- Review: a "to review" row on Today opens the charges that look like bill payments (Review and
  record, then Confirm with the bank's amount and date, or Not this) and the payments recorded on
  their own (Undo).
- Everyday spending on Today: this month against your average, and the top categories.
- Payments recorded on their own from the bank read "Paid … · auto". "Free after month" is now
  "Second half remaining", as on the web.

## [0.4.0]

- Bill reminders (More → Bill reminders): a notification around 8 AM on the day a bill is due, or
  one or two days before. Off unless turned on. Bills already recorded, or waiting to send, are
  skipped, and it works from the saved copy when offline.

## [0.3.3]

- Checks for an update every time the app opens fresh, as the Kairos app does, so a new release
  shows its badge right away.

## [0.3.2]

- The QR scanner is now inside the app (CameraX and ZXing, both open source), so it works without
  Google Play services. It asks for the camera only when you tap Scan QR code.

## [0.3.1]

- Update badge, as in the Kairos app: a blue dot on the More tab, an "Update available" row and a
  "New" pill on App version, and a notification once per new version that opens the Update screen.
- New Update screen: installed version, what's new, download & install, check for updates.
- Checks at launch and in the background about every two hours; the last result is kept if a check
  fails.

## [0.3.0]

- New design: Today, Month, Plan and More tabs, Figtree type and Phosphor icons.
- Recording is now pick, type on a number pad, Review, Confirm. The date can be picked from a calendar.
- Plan tab: the bills balance walked forward with a slider for "if I pay today".
- Appearance: light, dark or follow the phone, and six accent colors (Cobalt by default).
- New app icon.

## [0.2.0]

- What's kept on the phone (unsent changes, refused ones, the last copy of each month) is now in a
  Room database. Anything waiting from 0.1.x is moved across on first launch.
- Check for updates no longer shows an error when no release has been published.

## [0.1.1]

- Scan the web's QR code from inside the app (Scan QR code on the first screen). Phone cameras
  don't open `budget://` links.

## [0.1.0]

- First version: add this phone with a code or QR, see this month, record payments with a confirm
  step, update the bills balance, and see what's safe to send.
- Works offline: changes wait on the phone and send when it's back.
- Checks for updates and installs them from inside the app.
