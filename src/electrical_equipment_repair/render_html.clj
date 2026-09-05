(ns electrical-equipment-repair.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Drives the REAL actor stack -- `electrical-equipment-repair.operation/
  build` (a compiled langgraph-clj StateGraph) over the REAL seeded store
  (`electrical-equipment-repair.store/seed-db`), through the REAL Repair
  Governor (`electrical-equipment-repair.governor/check`) and the REAL
  rollout phase gate (`electrical-equipment-repair.phase/gate`), driven
  exactly the way this repo's own demo driver
  (`electrical-equipment-repair.sim`, `clojure -M:dev:run`) drives it:
  `langgraph.graph/run*` per request, `:resume? true` to hand a human
  approval back to the paused graph. Nothing on the page is written by
  hand:

    - every equipment/work-order row is read back out of the store after
      the run (`store/all-equipment`),
    - every ledger row, every HARD-hold rule name and every violation
      DETAIL STRING is the governor's own `:violations` entry off the
      append-only ledger (`store/ledger`) -- never a literal here,
    - the coordination-artifact record ids come from the store's own
      append-only histories (`store/repair-record-log-history`,
      `store/schedule-proposal-history`,
      `store/safety-concern-flag-history`,
      `store/supply-order-proposal-history`),
    - the dispatched safety-concern notices come from the mock
      Notifier's own send log (`notify/sent-log`),
    - the phase table is derived from `phase/phases` and the governor
      configuration from `governor/*` public vars,
    - the jurisdiction table is derived from `facts/catalog` +
      `facts/coverage` (honest coverage -- a jurisdiction with no
      spec-basis is shown as uncovered, never invented).

  The ONE exception is `op-gate-rows`, a static description of this
  actor's fixed closed-op contract; it is labelled as such at its
  definition site.

  Subject provenance (the demo may not invent subjects): every subject
  driven below is one of the six ids seeded by `store/demo-data`
  (`equipment-1` .. `equipment-6`) -- confirmed against the seed, and
  against `clojure -M:dev:run`, BEFORE this file was written.

  HARD holds are reached by genuinely violating this repo's own governor
  rules through the actor -- an uncovered jurisdiction, an unverified
  equipment record, an unresolved safety concern (twice: once seeded on
  `equipment-4`, and once created DURING this run by `equipment-1`'s own
  approved safety-concern flag), an op outside the closed allowlist, and
  -- through the actor's own documented `:advisor` injection seam -- a
  deliberately compromised advisor that tries to smuggle a
  re-energization sign-off marker and a non-`:propose` effect past the
  governor. No fact is ever appended to the ledger by this namespace.

  Deterministic: no clock, no randomness, no network, no timestamps in
  the page content. Re-running writes a byte-identical file.

  Run: `clojure -M:dev:render-html [out-file]`
  (default out-file `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [langgraph.graph :as g]
            [electrical-equipment-repair.advisor :as advisor]
            [electrical-equipment-repair.facts :as facts]
            [electrical-equipment-repair.governor :as governor]
            [electrical-equipment-repair.notify :as notify]
            [electrical-equipment-repair.operation :as op]
            [electrical-equipment-repair.phase :as phase]
            [electrical-equipment-repair.store :as store]))

;; ----------------------------- the run -----------------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :repair-technician :phase phase/default-phase})

(def ^:private approval
  {:status :approved :by "op-1"})

(def ^:private scenarios
  "One entry = one coordination request driven through the real actor.
  `:approval?` marks the scenarios where a human hands a decision back to
  the paused graph (`interrupt-before #{:request-approval}`); the resume
  only actually fires when the actor really did stop there.

  Every `:subject` is a seeded id from `store/demo-data`."
  [{:tid "t01" :request {:op :log-repair-record :subject "equipment-1"
                         :patch {:id "equipment-1"
                                 :diagnostic-notes "winding insulation resistance below spec on phase C"}}}

   {:tid "t02" :request {:op :schedule-repair-operation :subject "equipment-1"
                         :window {:proposed-start-date "2026-08-01"
                                  :proposed-end-date "2026-08-03"}
                         :notes "巻線再絶縁処理、無負荷試験"}}

   {:tid "t03" :approval? true
    :request {:op :flag-safety-concern :subject "equipment-1"
              :concern-type :insulation-failure
              :concern-description "巻線の絶縁抵抗値が基準未満、アーク・フラッシュのリスクあり、追加点検が必要。"}}

   ;; The flag above set equipment-1's own `:safety-concern-unresolved?`
   ;; ground-truth field. The very next schedule request is therefore HARD
   ;; held by check 6 -- a hold this run creates for itself, not one seeded
   ;; into the demo data.
   {:tid "t04" :request {:op :schedule-repair-operation :subject "equipment-1"
                         :window {:proposed-start-date "2026-08-04"
                                  :proposed-end-date "2026-08-05"}
                         :notes "懸念未解決のまま再スケジュールを試行"}}

   {:tid "t05" :request {:op :log-repair-record :subject "equipment-1"
                         :patch {:id "equipment-1" :safety-concern-unresolved? false}}}

   {:tid "t06" :request {:op :schedule-repair-operation :subject "equipment-1"
                         :window {:proposed-start-date "2026-08-05"
                                  :proposed-end-date "2026-08-06"}
                         :notes "再絶縁処理後の最終試験・通電前確認"}}

   {:tid "t07" :request {:op :order-supplies :subject "equipment-1"
                         :items ["magnet-wire-spool" "insulation-varnish"]
                         :cost-usd 650 :vendor "Local Motor Rewind Supply Co."}}

   {:tid "t08" :approval? true
    :request {:op :order-supplies :subject "equipment-1"
              :items ["replacement-stator-core"]
              :cost-usd 9000 :vendor "Heavy Electric Rebuild Supply"}}

   {:tid "t09" :request {:op :schedule-repair-operation :subject "equipment-2" :window {}}}
   {:tid "t10" :request {:op :schedule-repair-operation :subject "equipment-3" :window {}}}
   {:tid "t11" :request {:op :schedule-repair-operation :subject "equipment-4" :window {}}}
   {:tid "t12" :request {:op :direct-equipment-command :subject "equipment-1"}}

   {:tid "t13" :request {:op :schedule-repair-operation :subject "equipment-5"
                         :window {:proposed-start-date "2026-09-01"
                                  :proposed-end-date "2026-09-02"}}}
   {:tid "t14" :request {:op :schedule-repair-operation :subject "equipment-6"
                         :window {:proposed-start-date "2026-09-10"
                                  :proposed-end-date "2026-09-11"}}}])

(def ^:private compromised-scenarios
  "Scenarios driven through a SECOND actor built over the SAME store, whose
  Advisor has been swapped for a deliberately compromised one via
  `operation/build`'s own documented `:advisor` injection seam. The
  governor's structural checks 2 and 3 exist precisely for this case (see
  `governor` ns docstring: 'a compromised advisor gains nothing by
  trying') and are unreachable from the honest mock advisor, which never
  emits a non-`:propose` effect or a forbidden-action-class marker.

  `:tamper` receives the honest advisor's real proposal and returns the
  tampered one -- the request, the store and the governor are otherwise
  completely real."
  [{:tid "t15"
    :request {:op :schedule-repair-operation :subject "equipment-1"
              :window {:proposed-start-date "2026-08-20"
                       :proposed-end-date "2026-08-21"}
              :notes "再通電確定を紛れ込ませた提案（不正）"}
    :tamper #(assoc-in % [:value :re-energization-sign-off?] true)}

   {:tid "t16"
    :request {:op :log-repair-record :subject "equipment-1"
              :patch {:id "equipment-1" :diagnostic-notes "actuating effect smuggled onto a log op"}}
    :tamper #(assoc % :effect :actuate)}])

(defn- tampering-advisor
  "An Advisor that delegates to this repo's real mock advisor and then
  applies `f` to the proposal it produced."
  [f]
  (reify advisor/Advisor
    (-advise [_ st req] (f (advisor/infer st req)))))

(defn- drive!
  "Drives one scenario through `actor`. Resumes with a human approval only
  when the actor actually paused at `:request-approval`."
  [actor {:keys [tid request approval?]}]
  (let [r1 (g/run* actor {:request request :context operator} {:thread-id tid})
        escalated? (= :escalate (get-in r1 [:state :disposition]))
        r2 (when (and approval? escalated?)
             (g/run* actor {:approval approval} {:thread-id tid :resume? true}))
        final (or r2 r1)]
    {:tid tid
     :request request
     :escalated? escalated?
     :approved-by (when r2 (:by approval))
     :disposition (get-in final [:state :disposition])
     :verdict (get-in final [:state :verdict])}))

(defn run-demo!
  "Seeds a fresh MemStore, builds the real OperationActor over it, and
  drives every scenario. Returns `{:db .. :notifier .. :runs [..]}` -- every
  field the page renders is read back out of these afterwards."
  []
  (let [db (store/seed-db)
        notifier (notify/mock-notifier)
        actor (op/build db {:notifier notifier})
        runs (mapv #(drive! actor %) scenarios)
        compromised (mapv (fn [{:keys [tamper] :as s}]
                            (drive! (op/build db {:notifier notifier
                                                  :advisor (tampering-advisor tamper)})
                                    s))
                          compromised-scenarios)]
    {:db db :notifier notifier :runs (into runs compromised)}))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- fmt
  "Render a value, or an em dash when the domain model carries none."
  [v]
  (if (or (nil? v) (and (coll? v) (empty? v))) "—" (esc v)))

(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- flag [v]
  (cond (true? v)  "<span class=\"ok\">true</span>"
        (false? v) "<span class=\"warn\">false</span>"
        :else      "<span class=\"muted\">—</span>"))

(defn- tr [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" (esc %) "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- card [title lede body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       "    <p class=\"muted\">" lede "</p>\n"
       body
       "  </section>\n"))

;; ----------------------------- derived views -----------------------------

(defn- ledger-of [db] (vec (store/ledger db)))

(defn- holds [db]
  (filterv #(= :governor-hold (:t %)) (ledger-of db)))

(defn- disposition-cell [d]
  (case d
    :commit "<span class=\"ok\">commit</span>"
    :hold   "<span class=\"critical\">HARD hold</span>"
    :escalate "<span class=\"warn\">awaiting approval</span>"
    (str "<span class=\"muted\">" (fmt d) "</span>")))

(defn- last-fact-for [ledger subject]
  (last (filter #(= subject (:subject %)) ledger)))

(defn- status-cell [ledger subject]
  (let [f (last-fact-for ledger subject)]
    (case (:t f)
      :committed "<span class=\"ok\">committed</span>"
      :governor-hold (str "<span class=\"critical\">HARD hold · "
                          (esc (name (or (-> f :violations first :rule) :unknown)))
                          "</span>")
      :approval-rejected "<span class=\"warn\">rejected by approver</span>"
      "<span class=\"muted\">no activity</span>")))

(defn- summary-section [db runs]
  (let [led (ledger-of db)
        n (fn [t] (count (filter #(= t (:t %)) led)))]
    (card "Run summary"
          (str "Every number below is a count over the actor's own append-only ledger after "
               "driving " (count runs) " requests through the real "
               (code "electrical-equipment-repair.operation")
               " StateGraph at phase " (code (:phase operator))
               " (" (esc (:label (get phase/phases (:phase operator)))) ").")
          (table ["Measure" "Count"]
                 [(tr "requests driven through the actor" (count runs))
                  (tr "ledger facts (append-only)" (count led))
                  (tr "committed coordination artifacts" (n :committed))
                  (tr "governor HARD holds (un-overridable)" (n :governor-hold))
                  (tr "distinct governor rules triggered"
                      (count (distinct (mapcat #(map :rule (:violations %)) (holds db)))))
                  (tr "runs that paused for a human approver"
                      (count (filter :escalated? runs)))
                  (tr "repair-record-log entries" (count (store/repair-record-log-history db)))
                  (tr "schedule proposals" (count (store/schedule-proposal-history db)))
                  (tr "safety-concern flags" (count (store/safety-concern-flag-history db)))
                  (tr "supply-order proposals" (count (store/supply-order-proposal-history db)))]))))

(defn- equipment-section [db]
  (let [led (ledger-of db)]
    (card "Equipment / work-orders"
          (str "Ground truth as it stands AFTER the run — the "
               (code ":equipment-verified?") " and " (code ":safety-concern-unresolved?")
               " columns are the fields the governor independently re-checks (checks 4 and 6); "
               "the advisor's own confidence is never trusted for them. "
               (code "equipment-1") "'s concern column moved twice during this run: set by its "
               "approved safety-concern flag, cleared by a later repair-record log.")
          (table ["Id" "Equipment / work-order" "Jurisdiction" "verified?" "concern unresolved?" "Status" "Last ledger fact"]
                 (for [{:keys [id name jurisdiction equipment-verified?
                               safety-concern-unresolved? status]} (store/all-equipment db)]
                   (tr (code id) (esc name) (code jurisdiction)
                       (flag equipment-verified?)
                       (flag safety-concern-unresolved?)
                       (code (fmt status))
                       (status-cell led id)))))))

(defn- timeline-section [runs]
  (card "Run timeline"
        (str "One row per request actually driven through the actor, in order. "
             "The disposition column is the graph's own final "
             (code ":disposition") " channel; the approver column is only filled in when the "
             "graph really paused at " (code ":request-approval")
             " and a human resumed it.")
        (table ["#" "Op" "Subject" "Paused for a human?" "Approved by" "Disposition"]
               (for [{:keys [tid request escalated? approved-by disposition]} runs]
                 (tr (code tid)
                     (code (:op request))
                     (code (:subject request))
                     (if escalated? "<span class=\"warn\">yes</span>" "<span class=\"muted\">no</span>")
                     (fmt approved-by)
                     (disposition-cell disposition))))))

(defn- holds-section [db]
  (let [hs (holds db)]
    (card "Governor HARD holds"
          (str "Each row is a " (code ":governor-hold") " fact on the append-only ledger. "
               "The rule name and the detail text are the governor's own "
               (code ":violations") " entries — this page carries no rule text of its own. "
               "A HARD hold never reaches a human: it cannot be overridden by any approval.")
          (table ["Rule" "Op" "Subject" "Advisor confidence" "Governor's own detail"]
                 (for [h hs
                       v (:violations h)]
                   (tr (str "<span class=\"critical\">" (esc (:rule v)) "</span>")
                       (code (:op h))
                       (code (:subject h))
                       (fmt (:confidence h))
                       (esc (:detail v))))))))

;; -------------------------------------------------------------------------
;; The ONE hand-written block on this page: a static description of this
;; actor's FIXED closed-op contract (README `Closed op-allowlist`,
;; `governor`/`phase` ns docstrings). It is documentation of behaviour that
;; is fixed at design time, not runtime telemetry, so it is legitimately
;; described here rather than derived from the run. Everything else on the
;; page is read back out of the store / ledger / notifier after the run.
;; -------------------------------------------------------------------------
(def ^:private op-gate-rows
  [[":log-repair-record"
    "<span class=\"ok\">phase-3 auto-commit when governor-clean</span> · pure data logging, no capital or safety risk"]
   [":schedule-repair-operation"
    "<span class=\"ok\">phase-3 auto-commit when governor-clean</span> · a proposed diagnostic/repair/testing WINDOW only — never a live-work authorization"]
   [":flag-safety-concern"
    "<span class=\"warn\">ALWAYS a human approval · never auto at any phase</span> · enforced twice, by the governor's high-stakes set AND by every phase's auto set"]
   [":order-supplies"
    "<span class=\"warn\">phase-3 auto-commit below the cost threshold; escalates above it</span> · or whenever confidence falls under the floor"]])

(defn- contract-section []
  (card "Op gate (Repair Governor + rollout phase)"
        (str "This actor is COORDINATION-ONLY: every proposal it can produce carries "
             (code ":effect :propose") ", and it holds no repair-equipment/diagnostic-tool control "
             "authority and no return-to-service / re-energization sign-off authority — that is the "
             "licensed repair technician's exclusively. The closed allowlist is "
             (str/join ", " (map #(code %) (sort (map str governor/closed-op-allowlist))))
             "; the always-human set is "
             (str/join ", " (map #(code %) (sort (map str governor/high-stakes))))
             "; the supply-order escalation threshold is "
             (code (str "USD " governor/supply-order-cost-threshold-usd))
             " and the confidence floor is " (code governor/confidence-floor)
             " (all four read from the governor's own public vars).")
        (table ["Op" "Gate"]
               (for [[op gate] op-gate-rows]
                 (tr (code op) gate)))))

(defn- phase-section []
  (card "Rollout phases"
        (str "Derived from " (code "electrical-equipment-repair.phase/phases")
             ". The phase gate can only ADD caution: a governor HOLD always stays a HOLD, and an op "
             "that a phase permits to write but not to auto-commit escalates to a human even when the "
             "governor was completely clean.")
        (table ["Phase" "Label" "May write" "May auto-commit when clean"]
               (for [p (sort (keys phase/phases))
                     :let [{:keys [label writes auto]} (get phase/phases p)]]
                 (tr (code p) (esc label)
                     (if (seq writes) (str/join " " (map #(code %) (sort (map str writes)))) "<span class=\"muted\">—</span>")
                     (if (seq auto) (str/join " " (map #(code %) (sort (map str auto)))) "<span class=\"muted\">—</span>"))))))

(defn- jurisdiction-section []
  (let [{:keys [covered note]} (facts/coverage)]
    (card "Jurisdiction legal-basis catalog"
          (str "The " (code ":no-legal-basis") " HARD check is evaluated against this catalog. "
               (esc note) " Covered jurisdictions: " (code covered) ". A jurisdiction that is not in "
               "the catalog has NO spec-basis at all — the advisor must not fabricate one, and every "
               "schedule proposal for it is held (see " (code "equipment-2") ", jurisdiction "
               (code "ATL") ", in the holds table above).")
          (table ["ISO3" "Authority" "Numeric lead-time this actor could re-check" "Source"]
                 (for [iso3 (sort (keys facts/catalog))
                       :let [{:keys [owner-authority threshold-model repair-safety-provenance]}
                             (get facts/catalog iso3)]]
                   (tr (code iso3) (esc owner-authority)
                       (if (= :quantitative threshold-model)
                         (str "<span class=\"ok\">" (esc threshold-model) "</span>")
                         (str "<span class=\"muted\">" (esc threshold-model)
                              " · none — not fabricated</span>"))
                       (str "<a href=\"" (esc repair-safety-provenance) "\">"
                            (esc repair-safety-provenance) "</a>")))))))

(defn- artifacts-section [db]
  (let [rows (for [[label history] [["repair-record-log entry" (store/repair-record-log-history db)]
                                    ["schedule proposal" (store/schedule-proposal-history db)]
                                    ["safety-concern flag" (store/safety-concern-flag-history db)]
                                    ["supply-order proposal" (store/supply-order-proposal-history db)]]
                   r history]
               (tr (esc label)
                   (code (get r "record_id"))
                   (code (get r "equipment_id"))
                   (code (get r "jurisdiction"))
                   (flag (get r "immutable"))))]
    (card "Coordination artifacts committed"
          (str "The store's own append-only histories. Record ids are jurisdiction-scoped sequence "
               "numbers built by " (code "electrical-equipment-repair.registry")
               " — this actor invents no check-digit standard. None of these is an actuation: each is "
               "a logged record, a proposed window, a flag or a procurement proposal.")
          (table ["Kind" "Record id" "Equipment" "Jurisdiction" "Immutable"] rows))))

(defn- notices-section [notifier]
  (let [sent (notify/sent-log notifier)]
    (card "Safety-concern notices actually dispatched"
          (str "Read out of the mock Notifier's own send log. A notice is only ever dispatched by the "
               (code ":commit") " node for a " (code ":flag-safety-concern")
               " op — which never auto-commits at any phase, so a human had already approved every "
               "row below. The roster is the equipment/work-order's repair-technician and "
               "shop-safety-officer contacts, not the customer.")
          (table ["Channel" "To" "Subject / message"]
                 (for [{:keys [channel to subject message]} sent]
                   (tr (code channel) (code to) (esc (or subject message))))))))

(defn- ledger-section [db]
  (card "Audit ledger (this run)"
        (str "Every decision fact this scenario produced, in append order — the same log a "
             "regulator or a shop owner would read back. " (code ":basis")
             " is the governor's own rule list for a hold, and the advisor's own citations for a "
             "commit.")
        (table ["Fact" "Op" "Subject" "Basis"]
               (for [{:keys [t op subject basis]} (ledger-of db)]
                 (tr (code t) (code op) (code subject)
                     (if (seq basis)
                       (str/join "<br>" (map esc basis))
                       "<span class=\"muted\">—</span>"))))))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the whole operator-console document from the result of
  `run-demo!` (or any other real run of this actor)."
  [{:keys [db notifier runs]}]
  (str
   "<!doctype html>\n"
   "<html lang=\"en\"><head><meta charset=\"utf-8\">"
   "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
   "<title>cloud-itonami-isic-3314 &middot; electrical equipment repair &middot; operator console</title>"
   "<style>" (jp-go-dds.skin/dds+skin) "</style></head><body>\n"
   "<header class=\"bar\">\n"
   "  <h1>Repair of electrical equipment (ISIC 3314) — Operator Console</h1>\n"
   "  <p class=\"badge\">read-only sample · coordination-only · every proposal is <code>:effect :propose</code> · "
   "no repair-equipment control, no return-to-service or re-energization sign-off</p>\n"
   "</header>\n"
   "<main>\n"
   (summary-section db runs)
   (equipment-section db)
   (timeline-section runs)
   (holds-section db)
   (contract-section)
   (phase-section)
   (jurisdiction-section)
   (artifacts-section db)
   (notices-section notifier)
   (ledger-section db)
   "</main>\n"
   "<footer class=\"footer\">\n"
   "  <p class=\"muted\">Generated at build time by <code>electrical-equipment-repair.render-html</code> "
   "(<code>clojure -M:dev:render-html</code>) by running the real actor over a freshly seeded store. "
   "Deterministic — no clock, no randomness, no network. Re-running writes a byte-identical file.</p>\n"
   "</footer>\n"
   "</body></html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        hs (holds db)]
    ;; A console that shows no real HARD hold is not evidence of a governor.
    (when (empty? hs)
      (throw (ex-info "no :governor-hold fact on the ledger — refusing to write a console that shows no real hold"
                      {:ledger-facts (count (store/ledger db))})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p))
      (spit f (render result)))
    (println "wrote" out
             (str "(" (count (store/ledger db)) " ledger facts, "
                  (count hs) " HARD holds, "
                  (count (distinct (mapcat #(map :rule (:violations %)) hs)))
                  " distinct governor rules, "
                  (count (:runs result)) " requests)"))))
