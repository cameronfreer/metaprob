(ns metaprob.builtin-test
  (:require [clojure.test :refer :all]
            [metaprob.trace :refer :all]
            [metaprob.syntax :refer :all]
            [metaprob.builtin :as b]))

(deftest last-1
  (testing "Last element of a metaprob list"
    (is (= (b/last (b/seq-to-metaprob-list '(1 2 3)))
           3))))

(deftest append-1
  (testing "Concatenate two metaprob lists"
    (let [l1 (b/seq-to-metaprob-list '(1 2 3))
          l2 (b/seq-to-metaprob-list '(7 8 9))]
      (is (= (b/last (b/append l1 l2))
             9)))))

(deftest list-1
  (testing "Assemble and access a mp-list"
    (is (= (b/first (b/list 4 5 6))
           4))))

(deftest list-2
  (testing "Assemble and access a mp-list"
    (is (= (b/first (b/rest (b/list 4 5 6)))
           5))))

(deftest array2list
  (testing "Convert metaprob tuple to metaprob list"
    (let [v [5 7 11 13]
          t (b/seq-to-metaprob-tuple v)]
      (is (b/metaprob-tuple? t))
      (let [l (b/array_to_list t)]
        (is (b/metaprob-pair? l))
        (let [v2 (vec (b/metaprob-list-to-seq l))]
          (is (= v2 v)))))))

(deftest list2array
  (testing "Convert metaprob list to metaprob tuple"
    (let [v [5 7 11 13]
          t (b/seq-to-metaprob-list v)]
      (is (b/metaprob-pair? t))
      (let [l (b/list_to_array t)]
        (is (b/metaprob-tuple? l))
        (let [v2 (vec (b/metaprob-tuple-to-seq l))]
          (is (= v2 v)))))))

(deftest tag_capture
  (testing "capture_ and retrieve_tag_address smoke test"
    (let [root (new-trace "root")
          q (b/capture_tag_address root root root)
          a (b/list "this" "that")
          r (b/resolve_tag_address (b/pair q a))
          o2 (b/nth r 2)]
      (is (trace? o2))
      (b/trace_set o2 "value")
      (is (= (b/trace_get o2) "value"))
      (is (= (b/trace_get (b/lookup root a)) "value")))))

(deftest reification-1
  (testing "Does a program appear to be a trace?"
    (is (= (b/trace_get (program [] 7)) "prob prog"))))

(deftest length-1
  (testing "length smoke test"
    (is (= (b/length (b/mk_nil)) 0))
    (is (= (b/length (b/pair 0 (b/mk_nil))) 1))
    (is (= (b/length (b/seq-to-metaprob-list [1 2 3 4])) 4))))

(deftest range-1
  (testing "range smoke test"
    (let [r (b/range 5)]
      (is (= (b/length r) 5))
      (is (= (count (b/metaprob-list-to-seq r)) 5))
      (is (= (b/first r) 0))
      (is (= (b/last r) 4)))))

;; The real `map` is now in the prelude, but keeping the old one
;; because hard to throw away code I worked on!

(deftest map-1
  (testing "Map over a clojure list"
    (let [foo (b/mp-map (fn [x] (+ x 1))
                        (b/list 6 7 8))]
      (is (b/length foo) 3)
      (is (= (b/nth foo 0) 7))
      (is (= (b/nth foo 1) 8))
      (is (= (b/nth foo 2) 9)))))

(deftest map-2
  (testing "Map over a metaprob list"
    (is (= (b/first
            (b/rest
             (b/mp-map (fn [x] (+ x 1))
                       (b/pair 6 (b/pair 7 (b/pair 8 (b/mk_nil)))))))
           8))))

(deftest map-3
  (testing "Map over a metaprob array/tuple"
    (is (= (value-at (b/mp-map (fn [x] (+ x 1))
                               (tuple 6 7 8))
                     '(1))
           8))))

;; Test nondeterministic primitive

(deftest flip-1
  (testing "Try doing a flip"
    (is (boolean? (b/flip)))
    (is (boolean? (b/flip 0.5)))
    (let [proposer (b/trace_get (b/lookup b/flip (b/list "custom_choice_tracing_proposer")))
          target-val true
          target (new-trace target-val)
          ;; Args to prop are: params intervene target output
          result (proposer (b/mk_nil) (b/mk_nil) target (b/mk_nil))
          [val score] (b/metaprob-list-to-seq result)]
      (is (= val target-val))
      (is (> score -2))
      (is (< score 0)))))

;; trace_sites

(deftest trace_sites-1
  (testing "Smoke test trace_sites"
    (let [tree (trace-from-map {"x" (trace-from-map {"a" (new-trace 1)
                                                   "b" (new-trace 2)
                                                   "c" (new-trace)})
                               "y" (new-trace "d")})
          sites (b/trace_sites tree)]
      (is (= (b/length sites) 3)))))

;; match_bind

;; (deftest match_bind-1
;;   (testing "match_bind smoke"
;;     (let [env (make_env ... what a pain in the ass ...)]
;;       (b/match_bind (from_clojure '[a b])
;;                     [1 2]
;;                     env)
;;       (is (= (env_lookup env "a") 1))
;;       (is (= (env_lookup env "b") 2)))))


(deftest list-contains-1
  (testing "smoke test metaprob-list-contains"
    (is (b/metaprob-list-contains? (b/seq-to-metaprob-list '(3 5 7))
                                   5))))

(deftest list-contains-2
  (testing "smoke test metaprob-list-contains"
    (is (not (b/metaprob-list-contains? (b/seq-to-metaprob-list '(3 5 7))
                                        11)))))

(deftest set_difference-1
  (testing "smoke test set_difference"
    (let [a (b/seq-to-metaprob-list '(3 5 7))
          b (b/seq-to-metaprob-list '(5 7 11 13))]
      (is (b/metaprob-list-contains? a 5) "5 in a")
      (let [a-b (b/set_difference a b)
            b-a (b/set_difference b a)]
        (is (b/metaprob-list-contains? a-b 3) "3 in a-b")
        (is (b/metaprob-list-contains? b-a 13) "13 in b-a")
        (is (not (b/metaprob-list-contains? a-b 7)) "7 not in a-b")
        (is (not (b/metaprob-list-contains? b-a 7)) "7 not in b-a")))))

