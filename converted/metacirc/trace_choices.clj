;; This file was automatically generated

(ns metaprob.metacirc.trace-choices
  (:refer-clojure :only [ns declare])
  (:require [metaprob.syntax :refer :all]
            [metaprob.builtin :refer :all]
            [metaprob.prelude :refer :all]))

(declare trace_choices tc_eval)

(define
  trace_choices
  (program
    [program-noncolliding inputs intervention_trace output_trace]
    (if (trace_has_key program-noncolliding "custom_choice_tracer")
      (block
        (define
          tc_inputs
          (tuple inputs intervention_trace output_trace))
        (interpret
          (trace_get
            (lookup
              program-noncolliding
              (list "custom_choice_tracer")))
          tc_inputs
          (mk_nil)))
      (if (trace_has_key program-noncolliding "source")
        (block
          (define
            new_exp
            (lookup program-noncolliding (list "source" "body")))
          (define
            new_env
            (make_env
              (trace_get
                (lookup program-noncolliding (list "environment")))))
          (match_bind
            (lookup program-noncolliding (list "source" "pattern"))
            inputs
            new_env)
          (tc_eval new_exp new_env intervention_trace output_trace))
        (if (trace_has_key program-noncolliding "executable")
          (block
            (define
              val
              (interpret_prim
                (trace_get
                  (lookup program-noncolliding (list "executable")))
                inputs
                intervention_trace))
            (trace_set output_trace val)
            val)
          (block
            (pprint program-noncolliding)
            (error "Not a prob prog")))))))

(define
  tc_eval
  (program
    [exp env intervention_trace output_trace]
    (define
      walk
      (program
        [exp addr]
        (define
          v
          (if (eq (trace_get exp) "application")
            (block
              (define n (length (trace_subkeys exp)))
              (define
                values
                (map
                  (program
                    [i]
                    (walk (lookup exp (list i)) (add addr (list i))))
                  (range n)))
              (define oper (first values))
              (define name (trace_get (lookup oper (list "name"))))
              (trace_choices
                oper
                (rest values)
                (lookup intervention_trace (add addr (list name)))
                (lookup output_trace (add addr (list name)))))
            (if (eq (trace_get exp) "variable")
              (block
                (env_lookup
                  env
                  (trace_get (lookup exp (list "name")))))
              (if (eq (trace_get exp) "literal")
                (block (trace_get (lookup exp (list "value"))))
                (if (eq (trace_get exp) "program")
                  (block
                    (block
                      (define __trace_0__ (mk_nil))
                      (trace_set __trace_0__ "prob prog")
                      (trace_set
                        (lookup __trace_0__ (list "name"))
                        exp)
                      (trace_set_subtrace_at
                        __trace_0__
                        (list "source")
                        exp)
                      (trace_set
                        (lookup __trace_0__ (list "environment"))
                        env)
                      __trace_0__))
                  (if (eq (trace_get exp) "if")
                    (block
                      (define
                        pred
                        (walk
                          (lookup exp (list "predicate"))
                          (add addr (list "predicate"))))
                      (if pred
                        (block
                          (walk
                            (lookup exp (list "then"))
                            (add addr (list "then"))))
                        (block
                          (walk
                            (lookup exp (list "else"))
                            (add addr (list "else"))))))
                    (if (eq (trace_get exp) "block")
                      (block
                        (define n (length (trace_subkeys exp)))
                        (define
                          values
                          (map
                            (program
                              [i]
                              (walk
                                (lookup exp (list i))
                                (add addr (list i))))
                            (range n)))
                        (if (gt (length values) 0)
                          (last values)
                          (mk_nil)))
                      (if (eq (trace_get exp) "tuple")
                        (block
                          (define n (length (trace_subkeys exp)))
                          (define
                            values
                            (map
                              (program
                                [i]
                                (walk
                                  (lookup exp (list i))
                                  (add addr (list i))))
                              (range n)))
                          (list_to_array values))
                        (if (eq (trace_get exp) "definition")
                          (block
                            (define
                              subaddr
                              (name_for_definiens
                                (lookup exp (list "pattern"))))
                            (define
                              val
                              (walk
                                (lookup exp subaddr)
                                (add addr subaddr)))
                            (match_bind
                              (lookup exp (list "pattern"))
                              val
                              env))
                          (if (eq (trace_get exp) "this")
                            (block
                              (capture_tag_address
                                intervention_trace
                                (mk_nil)
                                output_trace))
                            (if (eq (trace_get exp) "with_address")
                              (block
                                (define
                                  tag_addr
                                  (walk
                                    (lookup exp (list "tag"))
                                    (add addr (list "tag"))))
                                (define
                                  [new_intervene _ new_output]
                                  (resolve_tag_address tag_addr))
                                (tc_eval
                                  (lookup exp (list "expression"))
                                  env
                                  new_intervene
                                  new_output))
                              (block
                                (pprint exp)
                                (error
                                  "Not a code expression")))))))))))))
        (if (trace_has (lookup intervention_trace addr))
          (block (trace_get (lookup intervention_trace addr)))
          (block v))))
    (walk exp (list))))

