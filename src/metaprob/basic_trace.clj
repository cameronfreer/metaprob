(ns metaprob.basic-trace
  (:require [clojure.string :as string]))

(def no-value '**no-value**)

(defprotocol ITrace
  "A prefix tree"
  (has-value? [_])
  (value [_] "The value stored for this trie (python: get)")
  (set-value! [_ val] "Store a value at root of this trie (python: set)")
  (clear-value! [_] "Remove any value")

  (has-subtrace? [_ key] "True iff this trie has a direct subtrace under the given key (python: has_key)")
  (subtrace [_ key] "The subtrace of this trie specified by the given key (python: _subtrace, sort of)")
  (set-subtrace! [_ key subtrace] "Splice a subtree as a child of this trie")
  (subtrace-location [_ key] "Returns locative if necessary")

  (has-value-at? [_ addr])
  (value-at [_ addr])
  (set-value-at! [_ addr val])

  (has-subtrace-at? [_ addr])
  (subtrace-at [_ addr] "(python: subtrace_at ??)")
  (set-subtrace-at! [_ addr subtrace] "(python: set_subtrace_at)")
  (subtrace-location-at [_ addr] "returns locative or trie as appropriate")

  (trace-keys [_])                      ;Return a seq
  (trace-count [_])                     ;Number of subtries

  (maybe-normalize [_])    ;If there's a trie corresponding to this trace, return it, else nil
  (blaze [_])
  (debug [_]))

(defn basic-trace? [x]
  (satisfies? ITrace x))

;; Should this throw an error if x is not a mutable trace?  I don't know.
;; ... how is this used?

(defn normalize
  ([tr]
   (normalize tr nil))
  ([tr info]
   (let [n (maybe-normalize tr)]
     (assert n ["failed to find this node" info])
     n)))

; Concrete implementation of the above interface

(declare ensure-subtrie-at)
(declare mutable-trace)
(declare trie?)
(declare new-locative)

(defn address? [x] (or (seq? x) (vector? x) (= x nil)))

(deftype Trie
  [^:volatile-mutable the-value
   ^:volatile-mutable subtries]    ; hash-map

  ; Implements the ITrace interface
  ITrace

  (has-value? [_]
    (not (= the-value no-value)))
  (value [_]
    (assert (not (= the-value no-value)) "no value")
    the-value)
  (set-value! [_ val]
    (assert (not (= val no-value)) "storing no value")
    (set! the-value val))
  (clear-value! [_]
    ;; Something fishy about this; if doing this causes the trie to become empty
    ;; then shouldn't the trie go away entirely?  Well, we don't have parent 
    ;; pointers, so nothing we can do about this.
    (set! the-value no-value))

  ;; Direct children
  (has-subtrace? [_ key]
    ;; python has_key, trace_has_key
    (contains? subtries key))
  (subtrace [_ key]
    (let [sub (get subtries key)]
      (assert (trie? sub) ["no such subtrie" key (trace-keys _) the-value])
      sub))
  (set-subtrace! [_ key sub]
    (assert trie? sub)
    (set! subtries (assoc subtries key sub))
    nil)

  (subtrace-location [_ key]
    (if (has-subtrace? _ key)
      (subtrace _ key)
      (new-locative _ key)))

  ;; Value at address
  (has-value-at? [_ addr]
    (assert (address? addr) addr)
    (if (empty? addr)
      (has-value? _)
      (let [[head & tail] addr]
        (and (has-subtrace? _ head)
             (has-value-at? (subtrace _ head) tail)))))
  (value-at [_ addr]
    (value (subtrace-at _ addr)))
  (set-value-at! [_ addr val]
    (assert (address? addr) addr)
    (set-value! (ensure-subtrie-at _ addr) val))

  ;; Descendant subtrie at address
  (has-subtrace-at? [_ addr]
    (assert (address? addr) addr)
    (if (empty? addr)
      true
      (let [[head & tail] addr]
        (and (has-subtrace? _ head)
             (has-subtrace-at? (subtrace _ head) tail)))))
  (subtrace-at [_ addr]
    ;; Assert: addr is a list (of symbols?)
    ;; Returns subtrie at address if it's there.
    ;; Fails (or nil) if no such subtree??  Maybe should soft-fail (return nil).
    (assert (address? addr) addr)
    (if (empty? addr)
      _
      (let [[head & tail] addr]
        (subtrace-at (subtrace _ head) tail))))
  (set-subtrace-at! [_ addr subtrie]
    ;; TBD: deal with case where subtrie is a locative
    (assert (trie? subtrie) subtrie)
    (assert (address? addr) addr)
    (let [[head & tail] addr]
      (if (empty? tail)
        (set-subtrace! _ head subtrie)
        (if (has-subtrace? _ head)
          (set-subtrace-at! (subtrace _ head) tail subtrie)
          (let [novo (mutable-trace)]
            (set-subtrace! _ head novo)
            (set-subtrace-at! novo tail subtrie))))))

  (subtrace-location-at [_trace addr]
    (assert (address? addr) addr)
    (letfn [(re [_trace addr]
              (if (empty? addr)
                _trace
                (let [[head & tail] addr]
                  (re (subtrace-location _trace head) tail))))]
      (re _trace addr)))

  (trace-keys [_] 
    (let [ks (keys subtries)]
      (if (= ks nil)
        '()
        ks)))
  (trace-count [_]
    (count subtries))

  (maybe-normalize [_] _)

  (blaze [_] _)
  (debug [_] :trie))

;; Not clear whether this is the most idiomatic / best approach.
(defn trie? [x]
  (instance? Trie x)
  ;; (= (type x) Trie)
  )

(defn mutable-trace
  ([]
   (Trie. no-value (hash-map)))
  ([val]
   (Trie. val (hash-map))))

(defn ensure-subtrie [tr key]
  (assert trie? tr)
  (if (has-subtrace? tr key)
    (subtrace tr key)
    (let [novo (mutable-trace)]
      (set-subtrace! tr key novo)
      novo)))

(defn ensure-subtrie-at [tr addr]
  ;; Assert: addr is a list (of strings, typically)
  ;; Similar to python subtrace_at or lookup ?
  ;; This should be called immediately before storing a value or subtrace.
  (assert trie? tr)
  (assert (address? addr) addr)
  (if (empty? addr)
    tr
    (let [[head & tail] addr]
      (ensure-subtrie-at (ensure-subtrie tr head)
                         tail))))

(deftype Locative
  [trie-or-locative this-key]

  ; Implements the ITrace interface
  ITrace

  (has-value? [_]
    (let [n (maybe-normalize _)]
      (if (trie? n)
        (has-value? n)
        false)))
  (value [_]
    (value (normalize _ this-key)))
  (set-value! [_ val]
    (set-value! (blaze _) val))
  (clear-value! [_]
    (let [n (maybe-normalize _)]
      (if (trie? n)
        (clear-value! n)
        nil)))

  ;; Direct children
  (has-subtrace? [_ key]
    (let [n (maybe-normalize _)]
      (if (trie? n)
        (has-value? n)
        false)))
  (subtrace [_ key]
    (subtrace (normalize _ this-key) key))
  (set-subtrace! [_ key subtrie]
    (set-subtrace! (blaze _) key subtrie))

  (subtrace-location [_ key]
    (let [n (maybe-normalize _)]
      (if (trie? n)
        (subtrace-location n key)
        (new-locative _ key))))

  ;; Value at address
  (has-value-at? [_ addr]
    (let [n (maybe-normalize _)]
      (if (trie? n)
        (has-value-at? n addr)
        false)))
  (value-at [_ addr]
    (value-at (normalize _ this-key) addr))
  (set-value-at! [_ addr val]
    (set-value-at! (blaze _) addr val))

  ;; Descendant subtrie at address
  (has-subtrace-at? [_ addr]
    (let [n (maybe-normalize _)]
      (if (trie? n)
        (has-subtrace-at? n addr)
        false)))
  (subtrace-at [_ addr]
    (subtrace-at (normalize _ this-key) addr))
  (set-subtrace-at! [_ addr subtrie]
    (set-subtrace-at! (blaze _) addr subtrie))

  (subtrace-location-at [_ addr]
    (letfn [(re [n addr]
              (if (empty? addr)
                n
                (let [[head & tail] addr]
                  (re (if (trie? n)
                        (subtrace-location n head)
                        (new-locative _ head))
                      tail))))]
      (re (or (maybe-normalize _) _) addr)))

  (trace-keys [_] 
    (let [n (maybe-normalize _)]
      (if (trie? n)
        (trace-keys n)
        '())))
  (trace-count [_]
    (let [n (maybe-normalize _)]
      (if (trie? n)
        (trace-count n)
        0)))

  ;; Returns trie or nil
  (maybe-normalize [_]
    (let [n (maybe-normalize trie-or-locative)]
      (if (trie? n)
        (if (has-subtrace? n this-key)
          (subtrace n this-key)
          nil)
        nil)))

  ;; Force the creation of a trie corresponding to this locative.
  (blaze [_]
    (let [tr (blaze trie-or-locative)]    ;Up one level
      (ensure-subtrie tr this-key)))

  (debug [_] [trie-or-locative this-key]))

(defn new-locative [tl key]
  (assert (not (basic-trace? key)))
  (Locative. tl key))

(defn locative? [x]
  (= (type x) Locative))

;; Utilities

(defn trie-from-map [val maap]
  (Trie. val maap))

