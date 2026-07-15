(ns electrical-equipment-repair.facts
  "Per-jurisdiction pre-repair de-energization / re-energization
  regulatory catalog -- the spec-basis table the Repair Governor checks
  every `:schedule-repair-operation` proposal against ('did the advisor
  cite an OFFICIAL public source for this jurisdiction's de-energize-
  before-work / verify-de-energized / qualified-personnel duty before
  electrical repair work begins, or did it invent one?'). Same honest-
  coverage discipline `installation.facts`/`demolition.facts`/
  `construction.facts`/`other-equipment-repair.facts` established for
  this fleet: a jurisdiction not in this table has NO spec-basis, full
  stop -- the advisor must not fabricate one, and the governor holds if
  it tries.

  Coverage is reported HONESTLY (see `coverage`); this is a STARTING
  catalog (JPN/USA/DEU), not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to `catalog`,
  cite a real source, done -- never invent a jurisdiction's requirements
  to make coverage look bigger.

  UNLIKE `installation.facts` (which found ONE `:quantitative`
  jurisdiction -- Japan's Industrial Safety and Health Act Article 88 --
  for an installation-notification PLAN filing), this catalog's research
  found ZERO `:quantitative` jurisdictions among JPN/USA/DEU for the
  pre-repair electrical de-energization duty itself: every seeded source
  is a PROCEDURAL requirement (de-energize, lock/tag/verify, use
  qualified/designated personnel, and -- JPN/DEU explicitly -- confirm
  safety BEFORE re-energizing/recommissioning) with no fixed numeric
  advance-notice-days count. This actor does NOT invent one:
    :qualitative -- the law imposes a documented de-energize-before-work
                    / verify-de-energized / qualified-personnel duty
                    before repair, inspection or testing work starts
                    (JPN/USA/DEU below), with NO fixed jurisdiction-wide
                    numeric lead-time this actor could independently
                    verify at the time this catalog was built.
                    `notification-lead-insufficient?` therefore always
                    returns `:qualitative` for a covered jurisdiction in
                    this catalog -- the Repair Governor's `legal-basis-
                    missing` HARD check (see `electrical-equipment-repair.
                    governor` ns docstring) is the bright line this
                    catalog actually supports; there is no numeric
                    lead-time bright line to independently re-check on
                    top of it.

  Real sources, verified before this catalog was written (no
  fabrication):
    JPN -- 労働安全衛生規則（昭和47年労働省令第32号）第339条（停電作業を行なう
           場合の措置）: when an employer has a worker perform electrical
           work (installation, inspection, repair or painting) by opening
           an electric circuit, the employer must (1) lock the switch
           used to open the circuit or post a notice prohibiting
           re-energization or station a supervisor during the work, (2)
           safely discharge any residual charge on a circuit carrying
           power cables/capacitors, and (3) confirm de-energization with
           a testing device and apply short-circuit grounding equipment
           for a high-voltage/extra-high-voltage circuit. Paragraph 2:
           BEFORE re-energizing (通電) a circuit that was opened for the
           work, the employer must first confirm that no worker faces an
           electric-shock hazard AND that any short-circuit grounding
           equipment has been removed -- an explicit pre-re-energization
           confirmation duty distinct from the pre-work de-energization
           duty itself -- https://laws.e-gov.go.jp/law/347M50002000032
    USA -- OSHA 29 CFR 1910.333 (Selection and use of work practices --
           Electrical safety-related work practices), (a)(1) Deenergized
           parts: live parts to which an employee may be exposed shall be
           deenergized before the employee works on or near them (subject
           to narrow infeasibility/increased-hazard exceptions); (b)
           Working on or near exposed deenergized parts: the circuits and
           equipment to be worked on shall be disconnected from all
           electric energy sources, control-circuit devices (push
           buttons, selector switches, interlocks) may not be used as the
           sole means of deenergizing, and a qualified person shall use
           test equipment to verify the deenergized condition (including
           checking for inadvertently induced voltage or backfeed) before
           work begins -- https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.333
    DEU -- DGUV Vorschrift 3 'Elektrische Anlagen und Betriebsmittel' §3
           (Prüfungen): electrical installations and equipment must be
           inspected/tested (geprüft) before first commissioning AND
           after any change (Änderung) or repair (Instandsetzung) before
           being put back into operation (vor der Wiederinbetriebnahme)
           -- an explicit pre-return-to-service testing duty for repaired
           electrical equipment specifically, carried out only by a
           suitably qualified electrician (Elektrofachkraft) --
           https://publikationen.dguv.de/widgets/pdf/download/article/1052.
           DGUV Vorschrift 3 is an accident-prevention regulation
           (Unfallverhütungsvorschrift, UVV) issued by the Deutsche
           Gesetzliche Unfallversicherung (the German statutory accident
           insurance body) rather than a directly-enacted federal
           statute, but it is legally binding on employers as a condition
           of their statutory accident-insurance membership, grounded in
           the same Directive 2009/104/EC (minimum safety and health
           requirements for the use of work equipment by workers)
           `other-equipment-repair.facts`'s DEU entry cites. UNLIKE that
           entry's generic BetrSichV §10 citation (Instandhaltung
           generally), this actor cites DGUV Vorschrift 3, the
           electrical-installation-specific instrument, because ISIC 3314
           is specifically electrical equipment repair.

  DEU is used as the EU-jurisdiction proxy, the SAME convention
  `installation.facts`/`demolition.facts`/`construction.facts`/
  `aerospace.facts`/`other-equipment-repair.facts` established -- there
  is no ISO-3166 alpha-3 code for the EU itself.")

(def catalog
  "iso3 -> requirement map. `:repair-safety-basis` / its `-provenance`,
  plus `:owner-authority`, are the citation the governor requires before a
  `:schedule-repair-operation` proposal can ever commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省（労働基準監督署長）"
          :repair-safety-basis "労働安全衛生規則（昭和47年労働省令第32号）第339条（停電作業を行なう場合の措置 -- 電気工事の作業のため電路を開路するときは開閉器の施錠・通電禁止表示・監視人の配置、残留電荷の放電、検電器具による停電確認及び高圧・特別高圧電路への短絡接地の措置を講じる義務。第2項: 開路した電路への通電に先立ち、感電の危険が無いこと及び短絡接地器具の取り外しを確認する義務）"
          :repair-safety-provenance "https://laws.e-gov.go.jp/law/347M50002000032"
          :threshold-model :qualitative
          :notification-lead-days nil
          :threshold-note "労働安全衛生規則第339条は停電作業前の施錠・検電・接地と、通電（再通電）前の安全確認を義務付けるが、固定日数の事前届出リードタイムは定めていない -- ここで数値を創作しない。"}
   "USA" {:name "United States"
          :owner-authority "Occupational Safety and Health Administration (OSHA), U.S. Department of Labor"
          :repair-safety-basis "29 CFR 1910.333 (Selection and use of work practices -- Electrical safety-related work practices): live parts to which an employee may be exposed must be deenergized before work begins (subject to narrow infeasibility/increased-hazard exceptions); circuits/equipment to be worked on must be disconnected from all electric energy sources, and a qualified person must verify the deenergized condition with test equipment (including checking for induced voltage or backfeed) before work starts"
          :repair-safety-provenance "https://www.osha.gov/laws-regs/regulations/standardnumber/1910/1910.333"
          :threshold-model :qualitative
          :notification-lead-days nil
          :threshold-note "OSHA's electrical safety-related work practices standard requires deenergization be verified by a qualified person BEFORE electrical repair work begins, but sets no fixed federal advance-notice-days count -- this actor does not invent one. This actor's `:schedule-repair-operation` still always requires an on-file legal-basis citation regardless (see `electrical-equipment-repair.governor` ns docstring `legal-basis-missing`)."}
   "DEU" {:name "Germany (EU jurisdiction proxy, see ns docstring)"
          :owner-authority "Deutsche Gesetzliche Unfallversicherung (DGUV) / zuständige Berufsgenossenschaft; EU level: European Parliament and Council"
          :repair-safety-basis "DGUV Vorschrift 3 'Elektrische Anlagen und Betriebsmittel' §3 (Prüfungen -- elektrische Anlagen und Betriebsmittel müssen vor der ersten Inbetriebnahme und nach einer Änderung oder Instandsetzung vor der Wiederinbetriebnahme durch eine Elektrofachkraft geprüft werden); grounded in Directive 2009/104/EC (minimum safety and health requirements for the use of work equipment by workers), which requires that in the case of repairs, modifications, maintenance or servicing, the workers concerned are specifically designated to carry out such work"
          :repair-safety-provenance "https://publikationen.dguv.de/widgets/pdf/download/article/1052"
          :threshold-model :qualitative
          :notification-lead-days nil
          :threshold-note "DGUV Vorschrift 3 §3は修理・変更後の再稼働（Wiederinbetriebnahme）前に有資格電気技術者（Elektrofachkraft）による検査を義務付けるのみで、日本の労働安全衛生規則第339条のような固定日数の事前届出リードタイムはEU/ドイツの電気設備関連規則では法定されていない -- ここで数値を創作しない。"}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any `:schedule-repair-operation` proposal
  that tries to cite one."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-3314 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `electrical-equipment-repair."
                 "facts/catalog`, never fabricate a jurisdiction's requirements.")})))

(defn notification-lead-insufficient?
  "Independently recompute whether a jurisdiction has a fixed numeric
  advance-notice-days requirement this actor could re-check. Three-valued,
  deliberately (the same shape `installation.facts/notification-lead-
  insufficient?` established):
    true/false   -- never produced by this catalog (see ns docstring):
                    none of JPN/USA/DEU carries a `:quantitative`
                    threshold-model for the pre-repair de-energization/
                    re-energization duty.
    :qualitative -- a jurisdiction with NO fixed numeric lead-time (every
                    covered jurisdiction in this catalog). This actor
                    cannot independently confirm 'sufficient' or
                    'insufficient' by arithmetic alone. Never fabricate a
                    lead-time here.
    nil          -- no spec-basis at all for `iso3` (a jurisdiction not in
                    `catalog`)."
  [iso3 _equipment]
  (when-let [{:keys [threshold-model]} (spec-basis iso3)]
    (case threshold-model
      :qualitative :qualitative
      nil)))
