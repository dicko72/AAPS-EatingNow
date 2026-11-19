# EN design: "up, not eaten yet" (fasting rise)

Status: built, not device-tested. Setting "Careful dosing before the first meal"
(`EatingNow_FastingRise`), off by default. Turn it on only after the basal review, so the two changes
can be judged one at a time.

## 1. Problem

In the morning BG often rises without food. The rise starts when the person gets up and moves
around ("feet on the floor": cortisol and adrenaline on waking). It lasts about 30-60 minutes and
then stops by itself.

The loop treats this rise like a meal:

- Stock oref UAM sees a positive deviation and projects it forward. In pure UAM mode `minPredBG` is
  the higher of the IOB and UAM minimums, capped at `avgPredBG` (the mean of the IOB and UAM
  eventual BG). So half of the UAM projection goes into the dose.
- Insulin peaks about 75 minutes after it is given. The rise is over before that. The insulin
  then works on a falling BG, and the result is a low about 80-90 minutes later.
- An SMB cannot be taken back. A zero temp can only take back basal.

Example from a real morning (no food): BG 7.8 and rising, IOB prediction 7.38, UAM prediction
10.38, target 5.5, ISF 5.0. Stock oref asks for (8.83 - 5.5) / 5.0 = 0.66 U. EN's ISF scaling
added only about 0.05 U. BG fell to 3.3 about 85 minutes later.

So this is mainly a stock oref behaviour, not something EN added. EN is still the right place to
fix it, because EN knows things oref does not: whether the person is up, and whether they have
eaten today.

### Negative IOB makes it worse

After a night low the loop gives zero temps, and IOB goes negative (often -0.3 to -0.8 U at the
start of the morning rise). oref reads negative IOB as "insulin is missing, BG will rise", so the
IOB prediction goes up. With ISF 5.0, -0.5 U adds about 2.5 mmol/L.

But the insulin is missing because the person needed less of it. It is a sign of sensitivity,
not a real shortfall. On a fasting morning it should not add to the dose.

## 2. The idea

Add a third daytime state:

| State | When | Dosing |
|---|---|---|
| `EN: Off` | outside EN, not started | overnight rules, as today |
| `EN: Fasting (steps 07:08)` (new) | EN is on, nothing eaten today | a short rise is treated as hormones, not food |
| `EN: Eaten (ENW 11:30)` | EN is on, a meal was logged today | normal EN daytime rules, as today |

The state is shown in the reason even when the setting is off. Only the dosing needs the setting.
"Fasting" was chosen over "Awake": with autostart the loop does not know she is up, but it always
knows whether a meal was logged.

The new state is useful because "EN on" (for example from the steps trigger) means "the person is
up". It does not mean "the person is about to eat". Without this state, the steps trigger would
switch on daytime dosing at the very start of the waking rise and give more insulin into it.

## 3. When the state is active

All of these must be true:

1. The setting is on (new setting, **off by default**).
2. EN is active (`ENActive`), whatever started it (steps, autostart, time).
3. Since EN Start Time there has been:
   - **no EN target** (`todaysENTargets` is empty), and
   - **no meal carbs** (see "rescue carbs" below), and
   - **no meal bolus**: type `NORMAL`, delivered by the pump (not record only), and at least the
     bolus trigger amount.

The state ends at the first ENW, meal carbs or meal bolus. It does not come back that day.

There is **no time limit**. The setting assumes that every meal is logged (an ENW or a manual
bolus). A user who sometimes eats without logging must not turn it on. The setting text must say
this clearly.

### Rescue carbs

Carbs taken for a low must not end the state. If they did, the loop would switch to meal dosing
right after a low.

Proposal: carbs do not count as a meal when BG was below target at the time they were entered.

### Record-only boluses

A record-only bolus (pen injection, or logged without a pump command) is saved by
`MasterActionExecutorImpl` through `DetailedBolusInfo.createBolus()`. That record has
`ids.pumpId = timestamp` but no `ids.pumpType` and no `ids.pumpSerial`. A bolus the pump
delivered is synced through `PumpSync` and has both set.

So: count a bolus only when `ids.pumpType != null`. **Check this against real data first**
(the Nightscout treatments of a pump bolus and of a record-only bolus) before relying on it.

Open point: should a pen injection count as food? Probably yes if the user uses pens for meals.
For now: it does not count, as agreed.

### When the bolus trigger is 0

Today a trigger of 0 means "boluses never count" (`todaysTriggerBoluses` is empty). For this
state that would be unsafe: a meal bolus would not end it. So in this state, when the trigger is
0, **any** delivered `NORMAL` bolus ends it. Ending the state too easily is the safe direction.

## 4. Dosing in this state

Only when the state is active **and** the resistance path is not authorised
(`isAuthorisedResistance` is false). A prebolus cannot happen here (no ENW).

1. **BG for the request: IOB prediction only.** `insulinReqBG = minOf(minPredBG, eventualBG, minIOBPredBG, lastIOBpredBG)`, so it is never above the AAPS BG.
   The UAM projection is not used. In the example this gives about 0.38 U instead of 0.66 U.
2. **Negative IOB counts as zero.** `insulinReq = insulinReq + min(0.0, iob_data.iob)`.
   This removes the part of the request that only comes from missing insulin. It is a bit more
   careful than exact, because with a long DIA the 4 h prediction holds only part of the IOB
   effect. Being careful is the right direction here.
3. **Profile ISF, no EN scaling.** `future_sens = sens`. No stronger ISF for a rise that is not
   food.
4. **The floor must follow the new request.** Today the resistance metering block ends with
   `insulinReq = max(insulinReq, insulinReqOrig)`, which puts back the stock oref request. In this
   state that line would undo steps 1-3. In this state the floor is the new request, not
   `insulinReqOrig`.
5. **SMB and temp basal work as normal** on the smaller request: SMB is half of the request
   (within `maxBolus`), and the temp is `basal + 2 × insulinReq`. The SMB gates do not change.

In the example: request about 0.38 U, SMB about 0.19 U.

## 5. What does not change

- **A long high still gets the resistance path.** Stuck high for more than 30 minutes, not falling,
  with the resistance IOB cap. This covers rises that are not food and not waking either: illness,
  a bad site, an occlusion, stress. Without this, the state could hold back insulin all day.
- Low and falling logic, Max IOB, the SMB gates, the overnight rules.
- Everything after the first meal.

## 6. Reason text

- EN status: `EN: Off`, `EN: Fasting (steps 07:08)` / `EN: Fasting (> 04:00)`, `EN: Eaten (ENW 11:30)`.
- `• EN:` note: `Fasting rise` when the state lowered the request, for example
  `insulinReq 0.66U→0.38U ... • EN: Fasting rise`. The `0.66U` stays visible so the stock request
  can still be compared.
- When negative IOB was removed, add it to the note: `Fasting rise, IOB<0 ignored (-0.45U)`.

## 7. Where the code goes

- `ENPlugin`: work out `hasEatenToday` from the lists it already builds (`todaysENTargets`,
  `todaysCarbs`, `todaysTriggerBoluses`), with the rescue-carb and record-only rules above. Pass a
  new field in `ENConfig`.
- `ENConfig` (`core/interfaces`, commonMain): one new Boolean field. This changes a shared data
  class, so run the full CI test command afterwards, not only the APS module.
- New `BooleanKey` and its preference entry. Update `app/src/testFull/resources/prefs-schema.txt`.
- `DetermineBasalEN`: the `insulinReqBG` choice, `future_sens`, the negative IOB step, the floor
  after the resistance metering, and the reason text.

## 8. How to check it

1. Before building: replay recent mornings from Nightscout. For each loop run in the state,
   compute the old and the new request, and look at what BG did 90 minutes later.
2. After building: device test. Read the reason text each morning: is `Fasting` shown, does it end
   at the first meal, and does a rescue carb leave it alone?
3. At review: time below 3.9 and 3.3 between waking and the first meal, and highs in the same
   period, compared with the weeks before.

## 9. Known limits of the first build

- A record-only bolus or rescue carbs above the trigger amounts still start an ENW (old behaviour).
  During that ENW the dosing is ENW dosing, and the reason can show `Fasting` together with `ENW: On`.
- A manual EN temp target counts as a meal only while it is running.

## 10. Open questions

1. **Name.** "Fasting" is short, but other names are possible ("Awake, no meal").
2. **Rescue carbs rule.** "BG below target when entered" is a guess. Maybe "BG below target or
   falling", or a gram limit as well.
3. **Before EN starts.** If the person gets up before EN Start Time, or steps do not reach the limit
   in time, the overnight rules apply and this state does not. Is that fine, or should the
   negative-IOB step also apply overnight?
4. **Early rise.** On some days there is a rise at 04:30-05:30 after a night low. It is not clear
   whether that is waking or a rebound from the low (and from rescue carbs). The state treats both
   the same, which is probably right, but it should be checked in the replay.
