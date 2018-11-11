import re

from log_conf import Logger
from utils import JUDGMENTS_FILE


class Judgment:
    def __init__(self, grade, qid, keywords, doc_id):
        self.grade = grade
        self.qid = qid
        self.keywords = keywords
        self.docId = doc_id
        self.features = []  # 0th feature is ranklib feature 1

    def __str__(self):
        return "grade:%s qid:%s (%s) docid:%s" % (self.grade, self.qid, self.keywords, self.docId)

    def to_ranklib_format(self):
        features_as_strs = ["%s:%s" % (idx+1, feature) for idx, feature in enumerate(self.features)]
        comment = "# %s\t%s" % (self.docId, self.keywords)
        return "%s\tqid:%s\t%s %s" % (self.grade, self.qid, "\t".join(features_as_strs), comment)


def _queries_from_header(lines):
    """ Parses out mapping between, query id and user keywords
        from header comments, ie:
        # qid:523: First Blood
        returns dict mapping all query ids to search keywords"""
    # Regex can be debugged here:
    # http://www.regexpal.com/?fam=96564
    regex = re.compile('#\sqid:(\d+?):\s+?(.*)')
    rVal = {}
    for line in lines:
        if line[0] != '#':
            break
        m = re.match(regex, line)
        if m:
            rVal[int(m.group(1))] = m.group(2)

    return rVal


def _judgments_from_body(lines):
    """ Parses out judgment/grade, query id, and docId in line such as:
         4  qid:523   # a01  Grade for Rambo for query Foo
        <judgment> qid:<queryid> # docId <rest of comment ignored...)"""
    # Regex can be debugged here:
    # http://www.regexpal.com/?fam=96565
    regex = re.compile('^(\d)\s+qid:(\d+)\s+#\s+(\w+).*')
    for line in lines:
        Logger.logger.info(line)
        m = re.match(regex, line)
        if m:
            Logger.logger.info("%s,%s,%s" % (m.group(1), m.group(2), m.group(3)))
            yield int(m.group(1)), int(m.group(2)), m.group(3)


def judgments_from_file(filename):
    with open(filename) as f:
        qid_to_keywords = _queries_from_header(f)
    with open(filename) as f:
        for grade, qid, docId in _judgments_from_body(f):
            yield Judgment(grade=grade, qid=qid, keywords=qid_to_keywords[qid], doc_id=docId)


def judgments_by_qid(judgments):
    r_val = {}
    for j in judgments:
        try:
            r_val[j.qid].append(j)
        except KeyError:
            r_val[j.qid] = [j]
    return r_val


if __name__ == "__main__":
    from sys import argv

    Logger.logger.info(len(argv))

    if len(argv) > 1 and len(argv[1]) > 0:
        judgement_file_name = argv[1]
    else:
        judgement_file_name = JUDGMENTS_FILE

    for judgment in judgments_from_file(judgement_file_name):
        Logger.logger.info(judgment)
