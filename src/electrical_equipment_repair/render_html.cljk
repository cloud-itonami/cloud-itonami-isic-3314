(ns electrical-equipment-repair.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: it previously had NO
  demo page and no generator at all. This namespace drives the REAL
  actor stack -- `electrical-equipment-repair.operation` (a compiled
  langgraph StateGraph) -> `electrical-equipment-repair.governor` ->
  `electrical-equipment-repair.store` -- through a scenario adapted from
  this repo's own `electrical-equipment-repair.sim` demo driver
  (`clojure -M:dev:run`, confirmed BEFORE writing this file to produce a
  sensible ledger against the real seeded ids `equipment-1`..
  `equipment-6`), then renders the resulting store, audit ledger and
  coordination-artifact registers.

  NOTHING on the page is hand-typed. Every id, jurisdiction, sequence
  number, disposition, hold rule and hold detail is read back out of the
  real store/ledger the run produced; the governor gate and phase ladder
  tables are derived from the live `electrical-equipment-repair.governor`
  / `electrical-equipment-repair.phase` vars rather than described in
  prose. The scenario INPUTS (which op against which equipment id) are of
  course authored -- that is what a scenario is -- but no OUTPUT is.

  ## Why this scenario

  It walks one equipment/work-order through a full coordination episode
  (log a repair record -> propose a repair window -> flag a safety
  concern -> log its resolution -> re-propose the window -> order
  supplies under, then over, the cost threshold), adds two
  cross-jurisdiction windows (USA and DEU/EU, both honestly qualitative
  -- this actor's `facts` catalog has NO `:quantitative` jurisdiction and
  never fabricates a numeric lead-time), and then exercises ALL SIX of
  the Repair Governor's HARD checks, each of which HOLDS without ever
  reaching a human:

    1. `:unknown-op`               -- an op outside the closed four-op allowlist
    2. `:effect-not-propose`       -- a deliberately ROGUE advisor injected over
                                      the SAME store, returning `:effect :actuate`.
                                      This check is unreachable from the shipped
                                      mock advisor (which always says `:propose`),
                                      so the only honest way to demonstrate the
                                      governor's defense-in-depth is to inject a
                                      malfunctioning advisor through the seam
                                      `operation/build` already exposes.
    3. `:forbidden-action-class`   -- a patch carrying this actor's electrical-
                                      domain-specific `:re-energization-sign-off?`
                                      marker
    4. `:equipment-not-verified`   -- `equipment-3`, `:equipment-verified? false`
    5. `:no-legal-basis`           -- `equipment-2`, jurisdiction ATL, not in
                                      `electrical-equipment-repair.facts`
    6. `:unresolved-safety-concern`-- `equipment-4`, concern open on file

  ## Determinism

  Every collaborator in the path is pure or deterministic: the mock
  advisor is a `case` over the request, the registry's reference numbers
  are jurisdiction-scoped zero-padded sequences, the mock notifier writes
  to an atom, and no code in `src/` reads a clock or a RNG. The page
  therefore contains NO timestamp and NO generated id, and two
  consecutive runs are byte-identical (verified by `cmp`).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [kotoba.lang.text :as str]
            [jp-go-dds.skin]
            [langgraph.graph :as g]
            [electrical-equipment-repair.advisor :as advisor]
            [electrical-equipment-repair.governor :as governor]
            [electrical-equipment-repair.notify :as notify]
            [electrical-equipment-repair.operation :as op]
            [electrical-equipment-repair.phase :as phase]
            [electrical-equipment-repair.store :as store]))

(def ^:private operator
  "The same operator context this repo's own `sim` driver uses."
  {:actor-id "op-1" :actor-role :repair-technician :phase 3})

;; ----------------------------- driving the REAL actor -----------------------------

(defn- record!
  "Append one finished graph run to the ordered run log. `result` is the
  raw `langgraph.graph/run*` return value -- everything rendered from it
  is real actor output."
  [runs tid request result]
  (swap! runs conj {:tid tid
                    :request request
                    :audit (vec (get-in result [:state :audit]))
                    :disposition (get-in result [:state :disposition])})
  result)

(defn- exec!
  "One operation, no human in the loop (auto-commit or HARD hold)."
  [runs actor tid request]
  (record! runs tid request
           (g/run* actor {:request request :context operator} {:thread-id tid})))

(defn- run-approve!
  "One operation that the phase gate / governor escalates, then resumed
  by a human approval. The resumed result carries the FULL accumulated
  audit (`:audit` reducer is `into`, restored from the checkpointer), so
  only the resumed result is recorded."
  [runs actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid})
  (record! runs tid request
           (g/run* actor {:approval {:status :approved :by "op-1"}}
                   {:thread-id tid :resume? true})))

(def ^:private rogue-advisor
  "A deliberately MALFUNCTIONING advisor -- it claims `:effect :actuate`,
  which the shipped mock advisor can never emit. Injected over the SAME
  store to prove HARD check 2 (`:effect-not-propose`) fires: a
  compromised or broken advisor gains nothing by trying. See ns
  docstring."
  (reify advisor/Advisor
    (-advise [_ _ request]
      {:summary    (str (:subject request) ": ROGUE advisor claiming a real-world actuation")
       :rationale  "この助言者は :effect :propose 以外を返す（本来あり得ない）"
       :cites      [:id]
       :effect     :actuate
       :value      {:id (:subject request)}
       :stake      nil
       :confidence 0.99})))

(defn run-demo!
  "Runs a fresh seeded store through the scenario described in the ns
  docstring. Returns `{:db :notifier :runs}` -- `:runs` is the ordered
  log of real graph results, `:db` the real store the actor wrote."
  []
  (let [db       (store/seed-db)
        notifier (notify/mock-notifier)
        actor    (op/build db {:notifier notifier})
        ;; same store, same notifier -- only the advisor is swapped
        broken   (op/build db {:notifier notifier :advisor rogue-advisor})
        runs     (atom [])]

    ;; --- clean lifecycle on equipment-1 (JPN) ---
    (exec! runs actor "t1" {:op :log-repair-record :subject "equipment-1"
                            :patch {:id "equipment-1"
                                    :diagnostic-notes "winding insulation resistance below spec on phase C"}})
    (exec! runs actor "t2" {:op :schedule-repair-operation :subject "equipment-1"
                            :window {:proposed-start-date "2026-08-01" :proposed-end-date "2026-08-03"}
                            :notes "巻線再絶縁処理、無負荷試験"})
    ;; ALWAYS escalates at every phase; the notice is really "sent" via the mock notifier on commit
    (run-approve! runs actor "t3" {:op :flag-safety-concern :subject "equipment-1"
                                   :concern-type :insulation-failure
                                   :concern-description "巻線の絶縁抵抗値が基準未満、アーク・フラッシュのリスクあり、追加点検が必要。"})
    (exec! runs actor "t4" {:op :log-repair-record :subject "equipment-1"
                            :patch {:id "equipment-1" :safety-concern-unresolved? false}})
    (exec! runs actor "t5" {:op :schedule-repair-operation :subject "equipment-1"
                            :window {:proposed-start-date "2026-08-05" :proposed-end-date "2026-08-06"}
                            :notes "再絶縁処理後の最終試験・通電前確認"})
    (exec! runs actor "t6" {:op :order-supplies :subject "equipment-1"
                            :items ["magnet-wire-spool" "insulation-varnish"]
                            :cost-usd 650 :vendor "Local Motor Rewind Supply Co."})
    ;; above governor/supply-order-cost-threshold-usd -> escalates, human approves
    (run-approve! runs actor "t7" {:op :order-supplies :subject "equipment-1"
                                   :items ["replacement-stator-core"]
                                   :cost-usd 9000 :vendor "Heavy Electric Rebuild Supply"})

    ;; --- cross-jurisdiction clean windows ---
    (exec! runs actor "t8" {:op :schedule-repair-operation :subject "equipment-5"
                            :window {:proposed-start-date "2026-09-01" :proposed-end-date "2026-09-02"}})
    (exec! runs actor "t9" {:op :schedule-repair-operation :subject "equipment-6"
                            :window {:proposed-start-date "2026-09-10" :proposed-end-date "2026-09-11"}})

    ;; --- all six HARD checks, none of which reaches a human ---
    (exec! runs actor "t10" {:op :direct-equipment-command :subject "equipment-1"})
    (exec! runs broken "t11" {:op :log-repair-record :subject "equipment-1"})
    (exec! runs actor "t12" {:op :log-repair-record :subject "equipment-1"
                             :patch {:id "equipment-1" :re-energization-sign-off? true}})
    (exec! runs actor "t13" {:op :schedule-repair-operation :subject "equipment-3" :window {}})
    (exec! runs actor "t14" {:op :schedule-repair-operation :subject "equipment-2" :window {}})
    (exec! runs actor "t15" {:op :schedule-repair-operation :subject "equipment-4" :window {}})

    {:db db :notifier notifier :runs @runs}))

;; ----------------------------- rendering helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-str [v] (if (keyword? v) (name v) (str v)))

(defn- bool-cell [v]
  (if (true? v)
    "<span class=\"ok\">yes</span>"
    "<span class=\"muted\">no</span>"))

(defn- fact-of [audit t] (first (filter #(= t (:t %)) audit)))

(defn- outcome
  "Classify one real run from its own audit trail. Never from a literal."
  [{:keys [audit disposition]}]
  (let [hold (fact-of audit :governor-hold)]
    (cond
      hold                            {:kind :hard-hold :violations (:violations hold)}
      (fact-of audit :approval-granted)
      {:kind :approved
       :reason (:reason (fact-of audit :approval-requested))
       :by (:by (fact-of audit :approval-granted))}
      (fact-of audit :approval-requested)
      {:kind :awaiting :reason (:reason (fact-of audit :approval-requested))}
      (= :commit disposition)         {:kind :auto-commit}
      :else                           {:kind :other})))

(defn- outcome-cell [o]
  (case (:kind o)
    :hard-hold  (str "<span class=\"critical\">HARD hold &middot; "
                     (esc (str/join ", " (map (comp kw-str :rule) (:violations o))))
                     "</span>")
    :approved   (str "<span class=\"ok\">escalated (" (esc (kw-str (:reason o)))
                     ") &rarr; approved by " (esc (:by o)) "</span>")
    :awaiting   (str "<span class=\"warn\">awaiting human approval &middot; "
                     (esc (kw-str (:reason o))) "</span>")
    :auto-commit "<span class=\"ok\">auto-commit (governor-clean)</span>"
    "<span class=\"muted\">in progress</span>"))

(defn- detail-cell [o]
  (case (:kind o)
    :hard-hold (esc (str/join " / " (map :detail (:violations o))))
    :approved  "<span class=\"muted\">human in the loop before commit</span>"
    :awaiting  "<span class=\"muted\">paused at :request-approval</span>"
    "<span class=\"muted\">&mdash;</span>"))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- rows [xs] (str/join "\n" xs))

;; ----------------------------- sections (all derived) -----------------------------

(defn- equipment-rows [db]
  (for [{:keys [id name jurisdiction equipment-verified? safety-concern-unresolved?
                status diagnostic-notes safety-contacts]} (store/all-equipment db)]
    (row (str "<code>" (esc id) "</code>")
         (esc name)
         (esc jurisdiction)
         (bool-cell equipment-verified?)
         (if (true? safety-concern-unresolved?)
           "<span class=\"critical\">open</span>"
           "<span class=\"ok\">none open</span>")
         (esc (kw-str status))
         (if (seq diagnostic-notes) (esc diagnostic-notes) "<span class=\"muted\">&mdash;</span>")
         (str "<span class=\"num\">" (count safety-contacts) "</span>"))))

(defn- run-rows [db runs]
  (for [{:keys [tid request] :as r} runs
        :let [o (outcome r)
              eq (store/equipment db (:subject request))]]
    (row (str "<code>" (esc tid) "</code>")
         (str "<code>" (esc (kw-str (:op request))) "</code>")
         (str "<code>" (esc (:subject request)) "</code>")
         (esc (or (:jurisdiction eq) "n/a"))
         (outcome-cell o)
         (detail-cell o))))

(defn- gate-rows
  "The action gate, DERIVED from the live governor/phase vars -- not a
  prose description that could drift away from the code."
  []
  (let [auto3 (get-in phase/phases [3 :auto])]
    (for [o (sort-by kw-str governor/closed-op-allowlist)
          :let [first-write-phase (first (for [p (sort (keys phase/phases))
                                               :when (contains? (:writes (get phase/phases p)) o)]
                                           p))]]
      (row (str "<code>" (esc (kw-str o)) "</code>")
           (if first-write-phase
             (str "<span class=\"num\">" first-write-phase "</span>")
             "<span class=\"muted\">never</span>")
           (if (contains? auto3 o)
             "<span class=\"ok\">may auto-commit when governor-clean</span>"
             "<span class=\"warn\">human approval, every phase</span>")
           (if (contains? governor/high-stakes o)
             "<span class=\"warn\">always high-stakes</span>"
             "<span class=\"muted\">no</span>")))))

(defn- phase-rows []
  (for [p (sort (keys phase/phases))
        :let [{:keys [label writes auto]} (get phase/phases p)]]
    (row (str "<span class=\"num\">" p "</span>")
         (esc label)
         (if (seq writes)
           (str/join " " (map #(str "<code>" (esc (kw-str %)) "</code>") (sort-by kw-str writes)))
           "<span class=\"muted\">none</span>")
         (if (seq auto)
           (str/join " " (map #(str "<code>" (esc (kw-str %)) "</code>") (sort-by kw-str auto)))
           "<span class=\"muted\">none</span>"))))

(defn- ledger-rows [db]
  (for [{:keys [t op subject disposition basis violations summary]} (store/ledger db)]
    (row (case t
           :committed "<span class=\"ok\">committed</span>"
           :governor-hold "<span class=\"critical\">governor-hold</span>"
           :approval-rejected "<span class=\"critical\">approval-rejected</span>"
           (esc (kw-str t)))
         (str "<code>" (esc (kw-str op)) "</code>")
         (str "<code>" (esc subject) "</code>")
         (esc (kw-str disposition))
         (if (seq violations)
           (esc (str/join ", " (map (comp kw-str :rule) violations)))
           (esc (str/join " ; " (map kw-str basis))))
         (if summary (esc summary) "<span class=\"muted\">&mdash;</span>"))))

(defn- artifact-rows [history]
  (for [r history]
    (row (str "<code>" (esc (get r "record_id")) "</code>")
         (esc (get r "kind"))
         (str "<code>" (esc (get r "equipment_id")) "</code>")
         (esc (get r "jurisdiction"))
         (bool-cell (get r "immutable")))))

(defn- notifier-rows [notifier]
  (for [{:keys [status channel to subject message]} (notify/sent-log notifier)]
    (row (esc (kw-str channel))
         (esc to)
         (esc (or subject message))
         (if (= :sent status)
           "<span class=\"ok\">sent</span>"
           (str "<span class=\"critical\">" (esc (kw-str status)) "</span>")))))

(defn- section [title lead headers body-rows]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n" (rows body-rows) "\n      </tbody>\n"
       "    </table>\n"
       "  </section>\n"))

;; ----------------------------- the document -----------------------------

(defn render
  "Renders the whole operator console from a `run-demo!` result. Takes no
  clock and no seed: identical input -> identical bytes."
  [{:keys [db notifier runs]}]
  (let [ledger      (vec (store/ledger db))
        outcomes    (mapv outcome runs)
        holds       (filterv #(= :governor-hold (:t %)) ledger)
        committed   (filterv #(= :committed (:t %)) ledger)
        approved    (filterv #(= :approved (:kind %)) outcomes)
        auto        (filterv #(= :auto-commit (:kind %)) outcomes)
        notices     (store/safety-concern-flag-history db)]
    (str
     "<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1, viewport-fit=cover\">"
     "<meta name=\"color-scheme\" content=\"light\">"
     "<title>cloud-itonami-isic-3314 &middot; repair of electrical equipment &mdash; operator console</title>"
     "<style>" (jp-go-dds.skin/dds+skin) "</style></head><body>\n"

     "<header class=\"bar\">\n"
     "  <h1>Repair of electrical equipment (ISIC 3314) &mdash; Operator Console</h1>\n"
     "</header>\n"
     "<p><span class=\"badge\">read-only sample</span> "
     "<span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">coordination-only &middot; every effect is :propose</span></p>\n"
     "<p class=\"subtitle\">Generated at build time by <code>electrical-equipment-repair.render-html</code> "
     "(<code>clojure -M:dev:render-html</code>) by actually running the compiled "
     "<code>electrical-equipment-repair.operation</code> StateGraph over a freshly seeded store. "
     "Every value below was read back out of that run &mdash; there is no mock markup on this page, "
     "and no timestamp, so successive regenerations are byte-identical.</p>\n"

     "<main>\n"

     (section "Run summary"
              "Counted from the real audit ledger and the real graph results, not asserted."
              ["Measure" "Count"]
              [(row "equipment / work-orders in the SSoT"
                    (str "<span class=\"num\">" (count (store/all-equipment db)) "</span>"))
               (row "graph runs in this scenario" (str "<span class=\"num\">" (count runs) "</span>"))
               (row "<span class=\"ok\">auto-commits (governor-clean, phase 3)</span>"
                    (str "<span class=\"num\">" (count auto) "</span>"))
               (row "<span class=\"ok\">escalated &rarr; human-approved commits</span>"
                    (str "<span class=\"num\">" (count approved) "</span>"))
               (row "<span class=\"critical\">HARD governor holds (never reach a human)</span>"
                    (str "<span class=\"num\">" (count holds) "</span>"))
               (row "committed facts in the audit ledger"
                    (str "<span class=\"num\">" (count committed) "</span>"))
               (row "audit-ledger facts total" (str "<span class=\"num\">" (count ledger) "</span>"))
               (row "confidence floor (<code>governor/confidence-floor</code>)"
                    (str "<span class=\"num\">" governor/confidence-floor "</span>"))
               (row "supply-order escalation threshold (USD)"
                    (str "<span class=\"num amt\">" governor/supply-order-cost-threshold-usd "</span>"))])

     (section "Equipment / work-order directory"
              "The SSoT after the run. <code>equipment-verified?</code> and the open-concern flag are the
               ground truth the Repair Governor re-checks independently &mdash; never the advisor's own confidence."
              ["Id" "Equipment / work-order" "Jurisdiction" "Verified?" "Safety concern" "Status"
               "Diagnostic notes" "Safety contacts"]
              (equipment-rows db))

     (section "Operation dispositions (this run)"
              "One row per graph run. The outcome and the hold reason are classified from each run's own
               audit trail; the detail text is the governor's own message."
              ["Thread" "Op" "Subject" "Jurisdiction" "Outcome" "Governor detail"]
              (run-rows db runs))

     (section "Action gate (Repair Governor)"
              "Derived from <code>governor/closed-op-allowlist</code>, <code>governor/high-stakes</code>
               and <code>phase/phases</code> &mdash; if the code changes, this table changes.
               All six governor checks are HARD: a human approver cannot override them."
              ["Op" "Writable from phase" "At phase 3" "Permanent escalation"]
              (gate-rows))

     (section "Rollout phase ladder"
              "Read straight out of <code>electrical-equipment-repair.phase/phases</code>."
              ["Phase" "Label" "Writes allowed" "May auto-commit"]
              (phase-rows))

     (section "Audit ledger"
              "Append-only decision facts the run actually wrote to the store."
              ["Fact" "Op" "Subject" "Disposition" "Basis / violated rule" "Summary"]
              (ledger-rows db))

     (section "Repair-record log"
              "Jurisdiction-scoped sequence numbers built by <code>electrical-equipment-repair.registry</code>."
              ["Record id" "Kind" "Equipment" "Jurisdiction" "Immutable"]
              (artifact-rows (store/repair-record-log-history db)))

     (section "Schedule proposals"
              "A proposed diagnostic / repair / testing WINDOW &mdash; never a live-work authorization
               and never a re-energization sign-off."
              ["Record id" "Kind" "Equipment" "Jurisdiction" "Immutable"]
              (artifact-rows (store/schedule-proposal-history db)))

     (section "Safety-concern flags"
              "Always human-approved before commit, at every phase."
              ["Record id" "Kind" "Equipment" "Jurisdiction" "Immutable"]
              (artifact-rows (store/safety-concern-flag-history db)))

     (section "Supply-order proposals"
              "A procurement PROPOSAL. No real order is ever placed by this actor."
              ["Record id" "Kind" "Equipment" "Jurisdiction" "Immutable"]
              (artifact-rows (store/supply-order-proposal-history db)))

     (section "Safety-concern notice dispatch"
              "The mock notifier's own send log &mdash; the notice really went out over both channels
               to every contact on the roster, after a human approved it."
              ["Channel" "To" "Subject / message" "Status"]
              (notifier-rows notifier))

     "  <section class=\"card\">\n"
     "    <h2>Safety-concern notice document</h2>\n"
     "    <p class=\"muted\">Rendered by <code>electrical-equipment-repair.registry/render-safety-concern-notice</code>
      and stored verbatim on the flag record &mdash; it cites the jurisdiction's own de-energization /
      re-energization legal basis inline, so the notice is self-evidencing.</p>\n"
     (str/join "\n" (for [n notices]
                      (str "    <pre><code>" (esc (get n "document")) "</code></pre>")))
     "\n  </section>\n"

     "</main>\n"
     "<footer>\n"
     "  <p>This actor NEVER controls repair equipment or diagnostic tools and NEVER signs off on\n"
     "  return-to-service or re-energization &mdash; that authority is the licensed repair technician's\n"
     "  exclusively. Every proposal carries <code>:effect :propose</code>; committing one means a\n"
     "  coordination artifact was logged, never that anything was energized.</p>\n"
     "  <p>Regenerate: <code>clojure -M:dev:render-html</code></p>\n"
     "</footer>\n"
     "</body></html>\n")))

;; ----------------------------- entry point -----------------------------

(defn -main [& args]
  (let [out    (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        ledger (vec (store/ledger (:db result)))
        holds  (filterv #(= :governor-hold (:t %)) ledger)
        commits (filterv #(= :committed (:t %)) ledger)]
    ;; Build-time invariant, not a convention: a console that shows only
    ;; happy paths is a brochure. If the scenario ever stops producing a
    ;; HARD governor hold, refuse to write the page at all.
    (when (zero? (count holds))
      (throw (ex-info (str "REFUSING to write " out
                           ": the run produced ZERO :governor-hold facts. "
                           "The operator console must demonstrate at least one HARD hold "
                           "that never reaches a human -- see electrical-equipment-repair.render-html "
                           "ns docstring. Fix the scenario (or the governor) before regenerating.")
                      {:out out :ledger-facts (count ledger) :holds 0
                       :committed (count commits)})))
    (when (zero? (count commits))
      (throw (ex-info (str "REFUSING to write " out
                           ": the run produced ZERO :committed facts, so the console would show "
                           "no clean/approved path at all.")
                      {:out out :ledger-facts (count ledger) :committed 0})))
    (let [f (java.io.File. ^String out)]
      (when-let [p (.getParentFile f)] (.mkdirs p)))
    (spit out (render result))
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  (count holds) " HARD governor holds, "
                  (count commits) " commits, "
                  (count (store/repair-record-log-history (:db result))) " repair records, "
                  (count (store/schedule-proposal-history (:db result))) " schedule proposals, "
                  (count (store/safety-concern-flag-history (:db result))) " safety-concern flags, "
                  (count (store/supply-order-proposal-history (:db result))) " supply orders)"))))
