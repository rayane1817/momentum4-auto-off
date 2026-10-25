# Design research: headphone companion apps (25 Oct 2026)

Goal: find what users actually like and dislike in headphone and audio companion apps, list the signs of generic "vibeslop" design to avoid, and say what gives an app a human touch. Input for `docs/design-spec-v2.md`.

Every claim is tagged:
- **[verified]**: I read the page myself this session.
- **[claimed]**: from a search-result summary or a secondary source; I did not read the original.
- **[opinion]**: my own judgement.

## Method and limits

- Reddit (`reddit.com`, `old.reddit.com`), Head-Fi and the Poweramp forum blocked fetches from this sandbox (HTTP 403 or a bot check). So there are **no Reddit quotes read first-hand** here. User voices come from App Store and review aggregator pages, press reviews, project READMEs and one support forum. Treat "users praise X" as a sample, not a survey.
- Our own Reddit evidence (the r/sennheiser threads in `CLAUDE.md`) is about behaviour, not design, so it is not reused here.
- The M4 desktop clients were read from their GitHub READMEs. I did not run them or see more than the README text.

---

## 1. Apps, one by one

### Sennheiser Smart Control Plus (the app we replace on Ryane's phone)
- App Store, Smart Control Plus: 4.8 from about 5K ratings. "Used the app to setup my headphones. Super easy and intuitive." Complaints are about missing controls, not looks: "The only thing it doesn't have that I'd like to have is the ability to edit the touch controls", and "I can't even turn the transparency mode off" (during calls). [verified: [App Store reviews](https://apps.apple.com/us/app/sennheiser-smart-control-plus/id6448098057?see-all=reviews&platform=iphone)]
- Changelog 1.1.1 lists "configurable tiles" and a redesigned Audio Modes screen. [claimed: search summary of the App Store page]
- Takeaway [opinion]: M4 owners mostly want the controls to exist and work. Our app already has more controls than SCP (call transparency, touch, comfort call, idle guard). The design job is clarity and trust, not adding features.

### Sony Sound Connect (was Headphones Connect)
- User reviews: "Very user-friendly, and the EQ is outstanding"; "The interface is nice looking and easy to navigate"; "I also own a Bose 700 and find their app easier to navigate"; "SonySoundConnect will only work in the vertical position"; "The equalizer: why such a small amount of controls, and why such specific frequencies?" [verified: [justuseapp review digest](https://justuseapp.com/en/app/1168502924/sony-headphones-connect/reviews)]
- Renamed and re-skinned in Oct 2024; a Spanish review called the old white and yellow scheme dated and preferred the new grey and blue. [claimed: [Audio46](https://audio46.com/blogs/audio46-news/sony-sound-connect-a-new-era-of-audio-integration)]
- Version 11.0.0 added "a simplified settings screen and shortcut function". [claimed: [APKMirror notes](https://apkmirror.com/apk/sony-corporation/sony-headphones-connect/sony-headphones-connect-11-0-0-release)]
- Takeaway [opinion]: praise is about reach (can I find the setting) and the EQ. Hate is about connection state that lies ("shows a connection that isn't real") and portrait-only layouts. Honest connection state matters more than polish.

### Nothing X
- "fairly straightforward and de-cluttered, with a user-friendly interface"; Simple tab with presets, Advanced EQ "gives you more precise tuning for each frequency, and you can save different sound profiles". [verified: [GSMArena, Headphone (1) review](https://www.gsmarena.com/nothing_headphone_1_review-news-68858.php)]
- "The 8-band custom equalizer lets you adjust both frequency and Q factor"; four presets plus a simpler 3-band EQ for less technical users; presets are stored on the headphones. [verified: [SoundGuys](https://www.soundguys.com/nothing-headphone-1-review-something-different-140323/)]
- "a simple application to use, with large buttons and well-labelled functions". [claimed: Tom's Guide via search summary]
- Visual identity: monochrome base, a dot-matrix display face (Ndot) used only for product names and numbers, red as a single accent. [claimed: [brand analysis](https://madegooddesigns.com/nothing-brand-guidelines/), sources disagree on how much red]
- Takeaway [opinion]: the strongest lesson is the **two-level EQ** (Simple presets first, Advanced behind it) and a **display face used sparingly**, only where it carries meaning (numbers, names).

### 1b. Nothing X, closer look (added 25 Oct 2026 for design v3)
- Home: "On the main screen, you should see some tiles. Tap the 'Equalizer' tile to open the preset menu." The preset menu has "an odd radar-looking adjustment panel, and some presets"; the advanced tool is a "parametric equalizer". [verified: [SoundGuys EQ presets](https://www.soundguys.com/what-do-all-the-nothing-headphone-1-eq-presets-sound-like-140790/)]
- Described as "Minimalist design and easy navigation", "clean and fluid"; features: ANC level, transparency, gesture controls, find my headphones, low-latency mode. [verified: [NothingTec](https://nothingtec.com/en/2025/01/20/que-puedes-hacer-con-la-app-nothing-x/), a fan blog]
- Battery per earbud and case on the main screen. [claimed: app listings via search summary]
- Nothing's web design system (third-party capture): strictly monochrome, no accent colour, uppercase monospace for labels and buttons, dot-matrix face only for product names, 6-8 px radii, no shadows. [verified: [shadcn.io capture of nothing.tech](https://www.shadcn.io/design/nothing/raw); describes the website, not the app]
- Not verified: exact current Nothing X home layout (no screenshots read). The v3 structure follows the verified "tiles open sub-screens" pattern, not a pixel copy.
- Takeaway [opinion]: Nothing's simplicity comes from **few things on the first screen**, **one type voice for numbers**, and **no colour except one interrupt**. All three transfer to the M4 app without copying Nothing's fonts or marks.

### AirPods settings on iOS
- Noise control lives in two places: Control Center (hold the volume slider, then the Noise Control button) and Settings. Adaptive combines ANC and Transparency and adjusts by itself. You choose which modes the stem press cycles through. [claimed: [Apple support guide](https://support.apple.com/is-is/guide/airpods/switch-between-noise-control-modes-dev9812f5cc3/1.0/web/1.0), via search summary]
- iOS 18 added a slider for Adaptive from "Less Noise" to "More Noise". [claimed: MacRumors via search summary]
- Takeaway [opinion]: Apple's design is mostly **absence**. Settings are plain grouped rows in the system style, and the daily controls live outside the app (Control Center). For us: the widget and Quick Settings tile are the "Control Center", the app is the settings page. Labelling slider ends with words ("Less Noise / More Noise") beats a bare 0-100.

### Bose (QuietComfort app)
- "precise control over the noise cancellation and Aware modes", but the overall verdict is mixed. [claimed: [Galaxus QC Ultra 2 review](https://www.galaxus.fr/en/page/testing-the-bose-quietcomfort-ultra-2-refined-not-revamped-40280)]
- A firmware update moved button shortcuts into the app: users said they now "had to look at the app to get battery level, change devices" and switched to Sony. [claimed: [AppleVis comment](https://applevis.com/comment/209153), [TechRadar](https://www.techradar.com/audio/wireless-headphones/i-am-extremely-angry-bose-quietcomfort-ultra-headphones-2nd-gen-reportedly-lost-key-features-after-a-firmware-update-and-users-arent-ok)]
- Takeaway [opinion]: people resent being forced into the app for basic actions. Widgets and tiles exist so you don't have to open it.

### Sonos (cautionary, not headphones)
- 2024 redesign: the app "lacked several key features" and "suffered from multiple bugs"; the CEO apologised for "significant problems with our new app". [verified: [MacRumors](https://www.macrumors.com/2024/07/25/sonos-app-update-apology/)]
- Takeaway [opinion]: a redesign that drops or moves working controls is worse than no redesign. Our Phase 3 rule (identical frames before and after) is the right guard.

### Jabra Sound+
- About 4.4 stars; "fairly intuitive and useful", "much more feature heavy than you could imagine or possibly need". "Moments" = predefined setting bundles for parts of the day. [claimed: [AppFollow listing](https://apps.appfollow.io/ios/jabra-sound/1320805565?country=us)]
- Takeaway [opinion]: feature-heavy is tolerated if the common path is short. Moments are close to our custom EQ presets; not worth copying further.

### Soundcore (Anker)
- Users asked for a quieter screen; a setting now turns off "discovery" prompts: "when I want a cleaner quieter interface then I can get it". [claimed: [Soundcore community](https://community.soundcore.com/t/new-app-version-2-6-2/1920214)]
- Takeaway [opinion]: promotional clutter is the top complaint for vendor apps. We have none; keep it that way (no tips, no banners, no "what's new" sheets).

### Wavelet (Android EQ)
- "as simple and straightforward as it gets": tap AutoEq, search your headphones, toggle on; graphic EQ below. "UX is easy enough to grasp, even for people who may not know too much about audio." Wants a way to "revert to the default settings". [verified: [Android Police](https://www.androidpolice.com/2020/05/20/wavelet-rootless-eq-individual-presets-for-2000-headphones/)]
- Takeaway [opinion]: one obvious reset (our "Flat") and presets before sliders.

### Poweramp equalizer
- Forum users on the knob UI: "I don't mind the round knobs, but I agree sliders would be nicer. I think Max is going for the look and feel of a quality classic audio amp"; others asked for the dB value under each band and said options are "becoming far too spread out over multiple screens". [claimed: search summary of [Poweramp forum](https://forum.powerampapp.com/topic/9725-suggestion-some-new-options-for-equalizer/); the page returned 403 to me]
- A third-party EQ developer: such apps "just add knobs/sliders in and expect the user to understand", argues for visual feedback (a curve). [claimed: F-Droid listing via search summary]
- Takeaway [opinion]: show the **number** next to each band and a **curve** that summarises the five bands. Skeuomorphic knobs are divisive; flat faders with a real tactile thumb are safer.

### Gadgetbridge
- 0.95.x release: "The charts, workout details, and device list have a refreshed design" and "multiple design consistency fixes across the app". [verified: [release notes](https://gadgetbridge.org/blog/release-0_95_01/)]
- Older criticism is about reliability, not looks ("far too buggy and limited" with Pebble). [claimed: [Tildes](https://tildes.net/~tech/1w5i/gadgetbridge_a_foss_alternative_to_your_fitness_watchs_app)]
- Takeaway [opinion]: in FOSS device apps, trust comes from honest connection state first.

### M4 desktop clients (credited in our README / prior-art)
- **omarchy-momentum4** (Linux bar widget): battery pill in the bar, a panel on click, scroll on the pill to change the noise level. Copy is plain and specific: "Charging is not shown. The headphones announce cable in and out but cannot be asked, and this widget polls." "Everything is read back from the headphones." [verified: [README](https://github.com/DanSmith888/omarchy-momentum4)]
- **momentum-4-gnome**: native widgets and the active theme; "Values come from the headset. Failed reads mark retained values stale and disable controls. Presets are saved only by explicit request." [verified: [README](https://github.com/ijaz-miks/momentum-4-gnome)]
- **SilentSoulsSr/SenheiserMomentum4** (Windows tray): tray-resident, connects on launch, no window required. [verified: [README](https://github.com/SilentSoulsSr/SenheiserMomentum4)]
- **Oein ZenControl** (macOS): "Model-aware UI: the window only shows controls the connected model actually supports". [verified: [README](https://github.com/Oein/sennheiser-desktop-client)]
- Takeaway [opinion]: the best of these share a voice: say what is known, what is not, and where the value came from. "Stale" values marked as stale is a pattern we should copy for the widget, which only shows the last known state.

### Small indie apps known for design
- **Symfonium** (music player): a user calls it "hands down the most evolved implementation of the latest Material Design iteration I've ever seen" and "a masterclass in just honing something into exactly what it needs to be, all from a complete design system". Others praise the logo's hidden detail. [verified: [Symfonium forum](https://support.symfonium.app/t/this-app-is-just-the-hottest-thing-ive-ever-seen/10616)]
- **Auxio**: "great clean design", fast, no extra features. [claimed: AlternativeTo via search summary]
- Takeaway [opinion]: users notice coherence (one system applied everywhere) and one crafted detail (the logo) more than any single screen.

---

## 2. Vibeslop signs to avoid

Sources: [prg.sh, "Why your AI keeps building the same purple gradient website"](https://prg.sh/ramblings/Why-Your-AI-Keeps-Building-the-Same-Purple-Gradient-Website) [verified], [925 Studios, "AI slop design tells"](https://www.925studios.co/blog/ai-slop-design-tells) [verified], [Axess Lab on glassmorphism](https://axesslab.com/?p=5111) [claimed].

| Sign | Why it reads as generic | Status of our v0.5-v0.10 UI |
|---|---|---|
| Purple or indigo accent, purple-to-blue gradients | The Tailwind `bg-indigo-500` default, repeated in training data; "that median is purple" [verified: prg.sh] | OK: teal, no gradients |
| Inter or Roboto with no hierarchy | "never anything with personality" [verified: prg.sh] | **Hit**: system sans everywhere, only bold vs regular |
| Rounded white cards on light grey, everywhere, same spacing | "Rounded corners on everything", "three rounded cards", identical intervals [verified: both] | **Hit**: 8 identical white cards, 16 dp radius, 12 dp gaps |
| Thin generic line icons on top of each card | [verified: 925 Studios] | OK: no icons |
| Glass, blur, translucent panels | Contrast fails over moving backgrounds [claimed: Axess Lab] | Partly: widget background is 95 % white over the wallpaper |
| Emoji or sparkle icons, "✨ AI" | [opinion] | Minor: ★ for custom presets |
| Vague copy ("Build faster. Ship smarter.") | "Weightless headline copy" [verified: 925 Studios] | OK: our copy is specific |
| Everything centred | [opinion] | OK |
| Every section the same weight | [opinion] | **Hit**: DEVICE and LOG look as important as NOISE CONTROL and AUTO POWER-OFF |
| Same uppercase grey label on every card | [opinion] | **Hit** |
| Gradient "Get Started" button | [verified: 925 Studios] | OK |

---

## 3. What gives an app a human touch

All [opinion] unless marked, drawn from the cases above.

1. **A typographic voice.** One deliberate second face used for one job. Nothing uses a dot-matrix face only for names and numbers [claimed]. For us: monospace digits for every measured value (battery, dB, minutes, firmware), like a readout on a piece of gear. System fonts only, no font files to license.
2. **A palette with a reason.** Colours from the object or the context, not from a UI kit. The M4 is a matte black or white shell with a woven headband; the Copper Special Edition adds copper detailing that reviewers liked ("adds visual interest") [claimed: [TechRadar](https://www.techradar.com/audio/headphones/sennheiser-momentum-4-wireless-special-edition-hit-new-low-price-in-amazons-black-friday-sale)]. A warm graphite with one copper accent is defensible; teal is arbitrary.
3. **Tactile controls.** Faders with a real cap instead of a dot, segmented controls that look like pressed keys, a visible scale with ticks. Poweramp's knobs show users notice when controls feel like hardware, and some find it too much [claimed]. Aim for "feels like a mixer", not "looks like a photo of a mixer".
4. **Honest, specific microcopy.** The M4 desktop clients are the model: "Charging is not shown...", "Failed reads mark retained values stale" [verified]. Say what the app knows, how it knows it, and what it cannot do. Sony's top hate is a connection state that lies [verified].
5. **One signature detail, used everywhere.** Symfonium users single out the logo's hidden detail [verified]. For us: a small copper "pilot light" dot meaning "live on the headphones right now", used for the connection, the selected noise mode, the active preset, the armed guard, and the widget's current mode. Nothing else uses copper.
6. **Asymmetry where it helps.** Give the two jobs people open the app for (noise mode, idle guard) more weight and size; push DEVICE, ADVANCED and LOG down and make them quieter. Not every section gets a card.
7. **Absence.** Apple's AirPods settings and Soundcore's "quieter interface" request point the same way [claimed]: no tips, no banners, no onboarding carousel.

---

## 4. Implications for our app (input to the spec)

- Daily controls belong in the widget and tile; the app is the settings page plus the guard. [opinion, from AirPods and Bose]
- Two-level EQ: presets first, faders below, with a curve and the dB number per band. [opinion, from Nothing X, Wavelet, Poweramp]
- Slider ends labelled in words ("ANC" / "Transparency"), value in monospace. [opinion, from AirPods]
- Connection state always visible and never optimistic; widgets say "last set" because they cannot read the headset. [opinion, from Sony, momentum-4-gnome]
- Keep every control where it is in function; restyle only. [opinion, from Sonos]
- Tiles: the system tints them; we only control a white 24 dp vector icon, the label (18 characters max) and the state. Google recommends at most two tiles per app and not using a tile to launch the app. [verified: [Android QS tiles guide](https://developer.android.com/develop/ui/views/quicksettings-tiles)]
- Widgets can follow the system light/dark setting through resources; Google's own path uses DayNight themes [verified: [widget theming guide](https://developer.android.com/develop/ui/views/appwidgets/enhance)]. We use plain resources in `values` / `values-night` instead, since we have no Material library.
