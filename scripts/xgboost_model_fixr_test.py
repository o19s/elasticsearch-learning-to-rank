from unittest import TestCase
from xgboost_model_fixr import find_min
from xgboost_model_fixr import find_first_feature
from xgboost_model_fixr import fix_tree


class ModelFixrTest(TestCase):

    def test_find_min(self):
        test_tree = {
            "children": [
                {
                    "children": [
                        {
                            "children": [
                                {"leaf": -0.0506895147, "nodeid": 15},
                                {"leaf": -0.190023482, "nodeid": 16}
                            ],
                            "depth": 3,
                            "missing": 15,
                            "no": 16,
                            "nodeid": 7,
                            "split": "entity:discorank",
                            "split_condition": 0.0777524188,
                            "yes": 15
                        },
                        {
                            "children": [
                                {"leaf": -0.112587906, "nodeid": 17},
                                {"leaf": 0.0141972378, "nodeid": 18}
                            ],
                            "depth": 3,
                            "missing": 17,
                            "no": 18,
                            "nodeid": 8,
                            "split": "match_phrase:title_exact",
                            "split_condition": 28.5359917,
                            "yes": 17
                        }
                    ],
                    "depth": 2,
                    "missing": 7,
                    "no": 8,
                    "nodeid": 3,
                    "split": "entity:discorank",
                    "split_condition": 3.87275314,
                    "yes": 7
                },
                {
                    "children": [
                        {
                            "children": [
                                {"leaf": -0.0938886553, "nodeid": 19},
                                {"leaf": -0.00217476487, "nodeid": 20}
                            ],
                            "depth": 3,
                            "missing": 19,
                            "no": 20,
                            "nodeid": 9,
                            "split": "match_and:common",
                            "split_condition": 31.170372,
                            "yes": 19
                        },
                        {
                            "children": [
                                {"leaf": -0.98676, "nodeid": 21},
                                {"leaf": 0, "nodeid": 22}
                            ],
                            "depth": 3,
                            "missing": 17,
                            "no": 18,
                            "nodeid": 8,
                            "split": "match_phrase:title_exact",
                            "split_condition": 28.5359917,
                            "yes": 17
                        }
                    ]
                }
            ]
        }

        result = find_min(test_tree)
        self.assertEqual(result, -0.98676)

    def test_find_min_unbalanced_1(self):
        test_tree = {
            "children": [
                {
                    "children": [
                        {
                            "children": [
                                {"leaf": -0.0506895147, "nodeid": 15},
                                {"leaf": -0.190023482, "nodeid": 16}
                            ],
                            "depth": 3,
                            "missing": 15,
                            "no": 16,
                            "nodeid": 7,
                            "split": "entity:discorank",
                            "split_condition": 0.0777524188,
                            "yes": 15
                        },
                        {
                            "children": [
                                {"leaf": -0.112587906, "nodeid": 17},
                                {"leaf": 0.0141972378, "nodeid": 18}
                            ],
                            "depth": 3,
                            "missing": 17,
                            "no": 18,
                            "nodeid": 8,
                            "split": "match_phrase:title_exact",
                            "split_condition": 28.5359917,
                            "yes": 17
                        }
                    ],
                    "depth": 2,
                    "missing": 7,
                    "no": 8,
                    "nodeid": 3,
                    "split": "entity:discorank",
                    "split_condition": 3.87275314,
                    "yes": 7
                },
                {
                    "children": [
                        {
                            "children": [
                                {"leaf": -0.0938886553, "nodeid": 19},
                                {
                                    "children": [
                                        {
                                            "children": [
                                                {"leaf": -0.0938886553, "nodeid": 19},
                                                {"leaf": -0.00217476487, "nodeid": 20}
                                            ],
                                            "depth": 3,
                                            "missing": 19,
                                            "no": 20,
                                            "nodeid": 9,
                                            "split": "match_and:common",
                                            "split_condition": 31.170372,
                                            "yes": 19
                                        },
                                        {
                                            "children": [
                                                {"leaf": -0.999, "nodeid": 21},
                                                {"leaf": 0, "nodeid": 22}
                                            ],
                                            "depth": 3,
                                            "missing": 17,
                                            "no": 18,
                                            "nodeid": 8,
                                            "split": "match_phrase:title_exact",
                                            "split_condition": 28.5359917,
                                            "yes": 17
                                        }
                                    ]
                                }
                            ],
                            "depth": 3,
                            "missing": 19,
                            "no": 20,
                            "nodeid": 9,
                            "split": "match_and:common",
                            "split_condition": 31.170372,
                            "yes": 19
                        },
                        {
                            "children": [
                                {"leaf": -0.98676, "nodeid": 21},
                                {"leaf": 0, "nodeid": 22}
                            ],
                            "depth": 3,
                            "missing": 17,
                            "no": 18,
                            "nodeid": 8,
                            "split": "match_phrase:title_exact",
                            "split_condition": 28.5359917,
                            "yes": 17
                        }
                    ]
                }
            ]
        }

        result = find_min(test_tree)
        self.assertEqual(result, -0.999)

    def test_find_min_unbalanced_2(self):
        test_tree = {
            "children": [
                {
                    "children": [
                        {
                            "children": [
                                {"leaf": -0.0506895147, "nodeid": 15},
                                {"leaf": -0.190023482, "nodeid": 16}
                            ],
                            "depth": 3,
                            "missing": 15,
                            "no": 16,
                            "nodeid": 7,
                            "split": "entity:discorank",
                            "split_condition": 0.0777524188,
                            "yes": 15
                        },
                        {
                            "children": [
                                {"leaf": -0.112587906, "nodeid": 17},
                                {"leaf": 0.0141972378, "nodeid": 18}
                            ],
                            "depth": 3,
                            "missing": 17,
                            "no": 18,
                            "nodeid": 8,
                            "split": "match_phrase:title_exact",
                            "split_condition": 28.5359917,
                            "yes": 17
                        }
                    ],
                    "depth": 2,
                    "missing": 7,
                    "no": 8,
                    "nodeid": 3,
                    "split": "entity:discorank",
                    "split_condition": 3.87275314,
                    "yes": 7
                },
                {
                    "children": [
                        {
                            "children": [
                                {"leaf": -0.999, "nodeid": 19},
                                {
                                    "children": [
                                        {
                                            "children": [
                                                {"leaf": -0.0938886553, "nodeid": 19},
                                                {"leaf": -0.00217476487, "nodeid": 20}
                                            ],
                                            "depth": 3,
                                            "missing": 19,
                                            "no": 20,
                                            "nodeid": 9,
                                            "split": "match_and:common",
                                            "split_condition": 31.170372,
                                            "yes": 19
                                        },
                                        {
                                            "children": [
                                                {"leaf": -0.1, "nodeid": 21},
                                                {"leaf": 0, "nodeid": 22}
                                            ],
                                            "depth": 3,
                                            "missing": 17,
                                            "no": 18,
                                            "nodeid": 8,
                                            "split": "match_phrase:title_exact",
                                            "split_condition": 28.5359917,
                                            "yes": 17
                                        }
                                    ]
                                }
                            ],
                            "depth": 3,
                            "missing": 19,
                            "no": 20,
                            "nodeid": 9,
                            "split": "match_and:common",
                            "split_condition": 31.170372,
                            "yes": 19
                        },
                        {
                            "children": [
                                {"leaf": -0.98676, "nodeid": 21},
                                {"leaf": 0, "nodeid": 22}
                            ],
                            "depth": 3,
                            "missing": 17,
                            "no": 18,
                            "nodeid": 8,
                            "split": "match_phrase:title_exact",
                            "split_condition": 28.5359917,
                            "yes": 17
                        }
                    ]
                }
            ]
        }

        result = find_min(test_tree)
        self.assertEqual(result, -0.999)

    def test_fix_tree(self):
        test_tree = [{
            "children": [
                {
                    "children": [
                        {
                            "children": [
                                {"leaf": -0.0506895147, "nodeid": 15},
                                {"leaf": -0.190023482, "nodeid": 16}
                            ],
                            "depth": 3,
                            "missing": 15,
                            "no": 16,
                            "nodeid": 7,
                            "split": "entity:discorank",
                            "split_condition": 0.0777524188,
                            "yes": 15
                        },
                        {
                            "children": [
                                {"leaf": -0.112587906, "nodeid": 17},
                                {"leaf": 0.0141972378, "nodeid": 18}
                            ],
                            "depth": 3,
                            "missing": 17,
                            "no": 18,
                            "nodeid": 8,
                            "split": "match_phrase:title_exact",
                            "split_condition": 28.5359917,
                            "yes": 17
                        }
                    ],
                    "depth": 2,
                    "missing": 7,
                    "no": 8,
                    "nodeid": 3,
                    "split": "entity:discorank",
                    "split_condition": 3.87275314,
                    "yes": 7
                },
                {
                    "children": [
                        {
                            "children": [
                                {"leaf": -0.999, "nodeid": 19},
                                {
                                    "children": [
                                        {
                                            "children": [
                                                {"leaf": -0.0938886553, "nodeid": 19},
                                                {"leaf": -0.00217476487, "nodeid": 20}
                                            ],
                                            "depth": 3,
                                            "missing": 19,
                                            "no": 20,
                                            "nodeid": 9,
                                            "split": "match_and:common",
                                            "split_condition": 31.170372,
                                            "yes": 19
                                        },
                                        {
                                            "children": [
                                                {"leaf": -0.1, "nodeid": 21},
                                                {"leaf": 0, "nodeid": 22}
                                            ],
                                            "depth": 3,
                                            "missing": 17,
                                            "no": 18,
                                            "nodeid": 8,
                                            "split": "match_phrase:title_exact",
                                            "split_condition": 28.5359917,
                                            "yes": 17
                                        }
                                    ]
                                }
                            ],
                            "depth": 3,
                            "missing": 19,
                            "no": 20,
                            "nodeid": 9,
                            "split": "match_and:common",
                            "split_condition": 31.170372,
                            "yes": 19
                        },
                        {
                            "children": [
                                {"leaf": -0.98676, "nodeid": 21},
                                {"leaf": 0, "nodeid": 22}
                            ],
                            "depth": 3,
                            "missing": 17,
                            "no": 18,
                            "nodeid": 8,
                            "split": "match_phrase:title_exact",
                            "split_condition": 28.5359917,
                            "yes": 17
                        }
                    ]
                }
            ]
        }]

        result = fix_tree(test_tree)
        self.assertEqual(result, {"depth": 0, "split": "entity:discorank", "missing": 1, "split_condition": 1, "yes": 1, "no": 2, "children": [{"leaf": 0.999, "nodeid": 1}, {"leaf": 0.999, "nodeid": 2}], "nodeid": 0})

    def test_find_first_feature(self):
        test_tree = {
            "children": [
                {
                    "children": [
                        {
                            "children": [
                                {"leaf": -0.0506895147, "nodeid": 15},
                                {"leaf": -0.190023482, "nodeid": 16}
                            ],
                            "depth": 3,
                            "missing": 15,
                            "no": 16,
                            "nodeid": 7,
                            "split": "entity:discorank",
                            "split_condition": 0.0777524188,
                            "yes": 15
                        },
                        {
                            "children": [
                                {"leaf": -0.112587906, "nodeid": 17},
                                {"leaf": 0.0141972378, "nodeid": 18}
                            ],
                            "depth": 3,
                            "missing": 17,
                            "no": 18,
                            "nodeid": 8,
                            "split": "match_phrase:title_exact",
                            "split_condition": 28.5359917,
                            "yes": 17
                        }
                    ],
                    "depth": 2,
                    "missing": 7,
                    "no": 8,
                    "nodeid": 3,
                    "split": "entity:discorank",
                    "split_condition": 3.87275314,
                    "yes": 7
                },
                {
                    "children": [
                        {
                            "children": [
                                {"leaf": -0.0938886553, "nodeid": 19},
                                {"leaf": -0.00217476487, "nodeid": 20}
                            ],
                            "depth": 3,
                            "missing": 19,
                            "no": 20,
                            "nodeid": 9,
                            "split": "match_and:common",
                            "split_condition": 31.170372,
                            "yes": 19
                        },
                        {
                            "children": [
                                {"leaf": -0.98676, "nodeid": 21},
                                {"leaf": 0, "nodeid": 22}
                            ],
                            "depth": 3,
                            "missing": 17,
                            "no": 18,
                            "nodeid": 8,
                            "split": "match_phrase:title_exact",
                            "split_condition": 28.5359917,
                            "yes": 17
                        }
                    ]
                }
            ]
        }

        result = find_first_feature(test_tree)
        self.assertEqual(result, "entity:discorank")