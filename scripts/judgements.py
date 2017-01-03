import re

def maybeParseQuery(line):
    stripped = line[1:].strip()
    if stripped.startswith('qid:'):
        qid, keyword = stripped[4:].split(':')
        return (int(qid), keyword.strip())
    return (-1, "")


def queriesFromHeader(lines):
    regex = re.compile('#\sqid:(\d+?):\s+?(.*)')
    rVal = {}
    for line in lines:
        if line[0] != '#':
            break
        m = re.match(regex, line)
        if m:
            rVal[int(m.group(1))] = m.group(2)

    return rVal

def judgmentsFromBody(lines):
    regex = re.compile('^(\d)\s+qid:(\d+)\s+#\s+(\w+).*')
    for line in lines:
        print(line)
        m = re.match(regex, line)
        if m:
            print("%s,%s,%s" % (m.group(1), m.group(2), m.group(3)))
            yield int(m.group(1)), int(m.group(2)), m.group(3)



if __name__ == "__main__":
    from sys import argv
    import pdb; pdb.set_trace()
    with open(argv[1]) as f:
        queriesFromHeader(f)
        for judgment, qid, docId in judgmentsFromBody(f):
            print(judgment)


