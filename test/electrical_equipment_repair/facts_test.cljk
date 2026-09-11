(ns electrical-equipment-repair.facts-test
  (:require [clojure.test :refer [deftest is]]
            [electrical-equipment-repair.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:repair-safety-provenance (facts/spec-basis "JPN"))))
  (is (= :qualitative (:threshold-model (facts/spec-basis "JPN"))))
  (is (nil? (:notification-lead-days (facts/spec-basis "JPN")))))

(deftest usa-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "USA"))))
  (is (nil? (:notification-lead-days (facts/spec-basis "USA")))))

(deftest deu-is-honestly-qualitative-not-fabricated
  (is (= :qualitative (:threshold-model (facts/spec-basis "DEU"))))
  (is (nil? (:notification-lead-days (facts/spec-basis "DEU")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "USA"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["JPN" "USA"] (:covered-jurisdictions report)))))

;; ----------------------------- notification-lead-insufficient? -----------------------------
;; UNLIKE installation.facts (JPN quantitative), this catalog found NO
;; quantitative jurisdiction for the pre-repair de-energization/
;; re-energization duty -- every covered jurisdiction always returns
;; :qualitative, never a fabricated true/false.

(deftest jpn-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/notification-lead-insufficient? "JPN" {})))
  (is (= :qualitative (facts/notification-lead-insufficient? "JPN" {:anything 100}))))

(deftest usa-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/notification-lead-insufficient? "USA" {})))
  (is (= :qualitative (facts/notification-lead-insufficient? "USA" {:anything 0}))))

(deftest deu-never-gets-a-fabricated-true-false
  (is (= :qualitative (facts/notification-lead-insufficient? "DEU" {})))
  (is (= :qualitative (facts/notification-lead-insufficient? "DEU" {:anything 0}))))

(deftest unknown-jurisdiction-returns-nil-not-a-guess
  (is (nil? (facts/notification-lead-insufficient? "ATL" {:anything 100}))))

(deftest no-jurisdiction-in-this-catalog-is-ever-quantitative
  (is (every? #(= :qualitative (:threshold-model (facts/spec-basis %))) (keys facts/catalog))
      "cloud-itonami-isic-3314's honest research found zero quantitative jurisdictions for the pre-repair de-energization/re-energization duty -- see ns docstring"))

;; ----------------------------- catalog citation honesty -----------------------------

(deftest jpn-cites-real-electrical-de-energization-law
  (let [sb (facts/spec-basis "JPN")]
    (is (re-find #"労働安全衛生規則" (:repair-safety-basis sb)))
    (is (re-find #"第339条" (:repair-safety-basis sb)))
    (is (re-find #"laws\.e-gov\.go\.jp" (:repair-safety-provenance sb)))))

(deftest usa-cites-real-osha-electrical-safety-work-practices-law
  (let [sb (facts/spec-basis "USA")]
    (is (re-find #"1910\.333" (:repair-safety-basis sb)))
    (is (re-find #"osha\.gov" (:repair-safety-provenance sb)))))

(deftest deu-cites-real-dguv-v3-provision
  (let [sb (facts/spec-basis "DEU")]
    (is (re-find #"DGUV Vorschrift 3" (:repair-safety-basis sb)))
    (is (re-find #"§3|Prüfungen" (:repair-safety-basis sb)))
    (is (re-find #"dguv\.de" (:repair-safety-provenance sb)))
    (is (re-find #"2009/104" (:repair-safety-basis sb))
        "cites the correct EU use-of-work-equipment directive backing the DGUV inspection duty")))

(deftest uncovered-jurisdiction-has-no-fabricated-catalog-entry
  (is (nil? (facts/spec-basis "ATL"))))
