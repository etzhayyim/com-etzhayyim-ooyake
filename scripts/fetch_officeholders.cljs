#!/usr/bin/env nbb
;; scripts/fetch_officeholders.cljs — ooyake 公 — 公人（現職の公職者）を Wikidata から
;; 取得して registry/gov-officeholders.*.edn を生成する（ADR-2607253000）。
;;
;;   nbb scripts/fetch_officeholders.cljs            # 全対象グループ
;;   nbb scripts/fetch_officeholders.cljs executive  # 1グループだけ
;;   nbb scripts/fetch_officeholders.cljs --dry-run  # 取得して件数だけ出す（書かない）
;;
;; ## 設計の要点
;;
;; **既存の :gov.unit/wikidata を主キーにして引く。** 人物名から検索するのではなく、
;; ooyake が既に持っている 7,087 の政府組織 QID それぞれについて「その組織の現職者は
;; 誰か」を訊く。だから取得した瞬間に :gov.person/unit → :gov.unit/id の連動が成立して
;; いて、後から名寄せする工程が要らない。組織側に QID が無ければその組織の公人も出ない
;; ——カバレッジの穴が構造的に見えるということでもある。
;;
;; **現職のみ。** P582（終了日）を持つ statement は除外し、deprecated rank も落とす。
;; 退任済みの人物を「現職」として記録しないための最低条件。ただし Wikidata の更新が
;; 遅れている国では退任者が残る——だから :gov.person/last-verified に取得日を必ず書き、
;; 「この日の Wikidata ではこうだった」以上のことを主張しない（G5）。
;;
;; **人間だけ。** ?person wdt:P31 wd:Q5 で絞る。P488（chairperson）等は組織が入って
;; いることがある。
;;
;; ## G6 の範囲（2026-07-25 改訂後）
;;
;; 取るのは「公職としての役職・氏名・在任開始・公式URL・QID」だけ。生年月日・出生地・
;; 家族・私的連絡先・住所・宗教・所属政党といった属性は **クエリに含めない**——
;; 取得してから捨てるのではなく、最初から要求しない。G10（never a target-list）は
;; 不変なので、個人の所在や日程に類する情報はこの経路に一切載らない。

(require '[clojure.edn :as edn]
         '[clojure.string :as str]
         '["node:fs" :as fs])

(def argv (vec *command-line-args*))
(def dry-run? (contains? (set argv) "--dry-run"))
(def only-group (first (remove #(str/starts-with? % "--") argv)))

(def endpoint "https://query.wikidata.org/sparql")

(def user-agent
  "WDQS は実在の連絡先を含む User-Agent を要求する（匿名の bot は遮断される）。"
  "ooyake/1.0 (+https://github.com/etzhayyim/com-etzhayyim-ooyake; world government atlas, public-office holders only)")

(def batch-size 60)
(def inter-batch-delay-ms 1500)

(def groups
  "取得対象。:file は registry の入力、:out は出力先の suffix。
  範囲は owner 指示（2026-07-25）の『元首＋政府首脳＋主要閣僚＋議会指導部＋監督機関トップ』。
  ministry 全体（1,642 件）ではなく外務・財務・国防の3分野に限っているのはその指示どおり。"
  ;; 国レベル unit は3ファイルに分かれている: world-countries.edn(174) は G20 級を
  ;; 含まず、それらは g20.edn(14) と seed.edn(5) に居る。world-countries.edn だけを
  ;; 引くと日本・米国・英国・独・中・仏・印・伯の元首/首脳が丸ごと欠ける（実測して
  ;; 気付いた）。level :country を持つファイルは3つとも入れる。
  [{:out "heads-of-state"      :files ["gov-units.world-countries.edn"
                                       "gov-units.g20.edn"
                                       "gov-units.seed.edn"]}
   {:out "executive"           :files ["gov-units.executive.edn"]}
   {:out "legislatures"        :files ["gov-units.world-legislatures.edn"]}
   {:out "courts"              :files ["gov-units.constitutional-courts.edn"]}
   {:out "centralbanks"        :files ["gov-units.world-centralbanks.edn"]}
   {:out "ministries-core"     :files ["gov-units.world-foreign.edn"
                                       "gov-units.world-finance.edn"
                                       "gov-units.world-defense.edn"]}
   {:out "oversight"           :files ["gov-units.oversight-anticorruption.edn"
                                       "gov-units.oversight-audit.edn"
                                       "gov-units.oversight-competition.edn"
                                       "gov-units.oversight-dataprotection.edn"
                                       "gov-units.oversight-electoral.edn"
                                       "gov-units.oversight-financial-regulator.edn"
                                       "gov-units.oversight-nhri.edn"
                                       "gov-units.oversight-ombudsman.edn"
                                       "gov-units.oversight-prosecutor.edn"
                                       "gov-units.oversight-revenue.edn"]}])

(def role-of
  "Wikidata property → ooyake の役職ラベル。ここに無い property は引かない。"
  {"head-of-state"   "Head of State"
   "head-of-government" "Head of Government"
   "chairperson"     "Chairperson"
   "director"        "Director / Head"
   "chief-executive" "Chief Executive"
   "officeholder"    "Officeholder"})

(defn- read-units [file]
  (:units (edn/read-string (str (.readFileSync fs (str "registry/" file) "utf8")))))

(defn- sparql [qids]
  (str "SELECT ?unit ?prop ?person ?personLabel ?native ?start WHERE {\n"
       "  VALUES ?unit { " (str/join " " (map #(str "wd:" %) qids)) " }\n"
       "  VALUES (?p ?ps ?prop) {\n"
       "    (p:P35 ps:P35 \"head-of-state\")\n"
       "    (p:P6 ps:P6 \"head-of-government\")\n"
       "    (p:P488 ps:P488 \"chairperson\")\n"
       "    (p:P1037 ps:P1037 \"director\")\n"
       "    (p:P169 ps:P169 \"chief-executive\")\n"
       "    (p:P1308 ps:P1308 \"officeholder\")\n"
       "  }\n"
       "  ?unit ?p ?st .\n"
       "  ?st ?ps ?person .\n"
       "  ?person wdt:P31 wd:Q5 .\n"
       ;; 現職のみ: 終了日を持つ statement と deprecated rank を落とす。
       "  FILTER NOT EXISTS { ?st pq:P582 ?endTime }\n"
       "  FILTER NOT EXISTS { ?st wikibase:rank wikibase:DeprecatedRank }\n"
       "  OPTIONAL { ?st pq:P580 ?start }\n"
       "  OPTIONAL { ?person wdt:P1559 ?native }\n"
       "  SERVICE wikibase:label { bd:serviceParam wikibase:language \"en\". }\n"
       "}"))

(defn- query! [qids]
  (-> (js/fetch endpoint
                #js {:method "POST"
                     :headers #js {"User-Agent" user-agent
                                   "Accept" "application/sparql-results+json"
                                   "Content-Type" "application/x-www-form-urlencoded"}
                     :body (str "query=" (js/encodeURIComponent (sparql qids)))})
      (.then (fn [resp]
               (if (.-ok resp)
                 (.then (.json resp) (fn [j] {:rows (js->clj (.. j -results -bindings) :keywordize-keys true)}))
                 (.then (.text resp) (fn [t] {:error (str "HTTP " (.-status resp) " " (subs t 0 (min 200 (count t))))})))))
      (.catch (fn [e] {:error (or (.-message e) (str e))}))))

(defn- sleep [ms] (js/Promise. (fn [res] (js/setTimeout res ms))))

(defn- qid-of [uri] (last (str/split uri #"/")))

(defn- slug [s]
  (-> (str s) str/lower-case (str/replace #"[^a-z0-9]+" "-") (str/replace #"^-|-$" "")))

(defn- today [] (subs (.toISOString (js/Date.)) 0 10))

(defn- ->person
  "1 SPARQL row + 元の unit → :gov.person/* エンティティ。"
  [by-qid row]
  (let [unit-qid (qid-of (get-in row [:unit :value]))
        unit (get by-qid unit-qid)
        prop (get-in row [:prop :value])
        person-qid (qid-of (get-in row [:person :value]))
        label (get-in row [:personLabel :value])
        native (get-in row [:native :value])
        start (get-in row [:start :value])]
    (when (and unit label (not (re-matches #"Q\d+" label)))
      (cond-> {:gov.person/id (str "person."
                                   (str/replace (:gov.unit/id unit) #"^gov\." "")
                                   "." (slug prop))
               :gov.person/unit (:gov.unit/id unit)
               :gov.person/jurisdiction (:gov.unit/jurisdiction unit)
               :gov.person/role-en (get role-of prop prop)
               :gov.person/role-property prop
               :gov.person/name-en label
               :gov.person/wikidata person-qid
               :gov.person/unit-wikidata unit-qid
               :gov.person/sourcing :authoritative
               :gov.person/provenance "wikidata"
               :gov.person/last-verified (today)}
        (and native (not= native label)) (assoc :gov.person/name-native native)
        start (assoc :gov.person/since (subs start 0 10))
        (:gov.unit/official-url unit) (assoc :gov.person/official-url (:gov.unit/official-url unit))))))

(defn- dedupe-ids
  "2段階。

  (1) **同一人物の重複行をまとめる。** SPARQL の `OPTIONAL ?native`（P1559 母語表記）は
  多言語表記を持つ人物で行を掛け算する — インドの Narendra Modi / Droupadi Murmu は
  それぞれ4行、高市早苗は2行になっていた（実測）。同じ (unit, 役職, 人物QID) は1件に
  畳む。

  (2) **本当に別人が同じ職に並ぶ場合だけ連番を振る。** スイスの連邦参事会のような
  合議制や共同元首を『1人の長が居る』と潰さないため、ここでは人を落とさない。"
  [people]
  (->> people
       (group-by (juxt :gov.person/unit :gov.person/role-property :gov.person/wikidata))
       (map (fn [[_ dupes]] (first (sort-by #(- (count (str %))) dupes))))
       (group-by :gov.person/id)
       (mapcat (fn [[id group]]
                 (let [sorted (sort-by :gov.person/wikidata group)]
                   (if (= 1 (count sorted))
                     sorted
                     (map-indexed (fn [i p] (assoc p :gov.person/id (str id "." (inc i)))) sorted)))))
       (sort-by :gov.person/id)
       vec))

(defn- render [out people source-files]
  (str ";; ooyake 公 — gov-officeholders." out ".edn — 現職の公職者（公人）。自動生成、手編集しない。\n"
       ";; 生成: nbb scripts/fetch_officeholders.cljs " out "   (" (today) ")\n"
       ";; 出典: Wikidata（P35 元首 / P6 政府首脳 / P488 議長 / P1037 長 / P169 CEO /\n"
       ";;       P1308 現職者）。P582(終了日) を持つ statement と deprecated rank は除外\n"
       ";;       済み＝取得時点の現職のみ。:gov.person/last-verified がその取得日。\n"
       ";; 主キー: 既存 registry の :gov.unit/wikidata。したがって :gov.person/unit は\n"
       ";;       常に実在の :gov.unit/id を指す（名寄せ不要）。入力: " (str/join ", " source-files) "\n"
       ";;\n"
       ";; G6（2026-07-25 改訂）: 公職としての役職・氏名・在任開始・公式URL のみ。生年月日・\n"
       ";; 出生地・家族・私的連絡先・住所・政党はクエリに含めていない（捨てているのではなく\n"
       ";; 要求していない）。G10 civic-wayfinding-never-a-target-list は不変。\n"
       ";; G11 politically neutral: 記録するのは『誰がその職に就いているか』だけで、評価・\n"
       ";; ランキング・スコアは持たない。\n"
       "\n{:graph {:name \"gov-atlas-v1\" :visibility :public}\n\n :people\n ["
       (str/join "\n\n  " (map pr-str people))
       "]}\n"))

(defn- run-group! [{:keys [out files]}]
  (let [units (mapcat read-units files)
        with-qid (filter :gov.unit/wikidata units)
        by-qid (into {} (map (juxt :gov.unit/wikidata identity) with-qid))
        batches (partition-all batch-size (map :gov.unit/wikidata with-qid))]
    (println (str "\n[" out "] " (count units) " units, " (count with-qid) " with QID, "
                  (count batches) " batches"))
    (letfn [(step [remaining acc n-err]
              (if (empty? remaining)
                (js/Promise.resolve [acc n-err])
                (-> (query! (first remaining))
                    (.then (fn [{:keys [rows error]}]
                             (if error
                               (do (println (str "  batch " (- (count batches) (count remaining) -1)
                                                 "/" (count batches) " FAILED: " error))
                                   (-> (sleep inter-batch-delay-ms)
                                       (.then #(step (rest remaining) acc (inc n-err)))))
                               (let [people (keep #(->person by-qid %) rows)]
                                 (println (str "  batch " (- (count batches) (count remaining) -1)
                                               "/" (count batches) " → " (count people) " officeholder(s)"))
                                 (-> (sleep inter-batch-delay-ms)
                                     (.then #(step (rest remaining) (into acc people) n-err))))))))))]
      (-> (step (vec batches) [] 0)
          (.then (fn [[people n-err]]
                   (let [final (dedupe-ids people)
                         path (str "registry/gov-officeholders." out ".edn")]
                     (println (str "[" out "] " (count final) " officeholders across "
                                   (count (distinct (map :gov.person/unit final))) " units"
                                   (when (pos? n-err) (str "; " n-err " batch(es) failed"))))
                     (when-not dry-run?
                       (.writeFileSync fs path (render out final files))
                       (println (str "[" out "] wrote " path)))
                     {:out out :n (count final) :errors n-err})))))))

(defn -main []
  (let [targets (if only-group (filter #(= only-group (:out %)) groups) groups)]
    (when (empty? targets)
      (println (str "unknown group " (pr-str only-group) "; known: "
                    (str/join " " (map :out groups))))
      (.exit js/process 2))
    (letfn [(step [remaining acc]
              (if (empty? remaining)
                (js/Promise.resolve acc)
                (-> (run-group! (first remaining))
                    (.then #(step (rest remaining) (conj acc %))))))]
      (-> (step (vec targets) [])
          (.then (fn [results]
                   (println (str "\ntotal: " (reduce + (map :n results)) " officeholders, "
                                 (reduce + (map :errors results)) " failed batch(es)"
                                 (when dry-run? " (dry-run — nothing written)")))))))))

(-main)
