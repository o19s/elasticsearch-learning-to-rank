import logging.config


def singleton(cls):
    instances = {}

    def get_instance():
        if cls not in instances:
            instances[cls] = cls()
        return instances[cls]
    return get_instance()


@singleton
class Logger:
    def __init__(self):
        # logging.config.fileConfig('logging.conf')
        self.logger = logging.getLogger('__name__')
        self.logger.setLevel(logging.DEBUG)

        # create console handler and set level to debug
        ch = logging.StreamHandler()
        ch.setLevel(logging.DEBUG)
        # formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
        formatter = logging.Formatter('%(message)s')
        ch.setFormatter(formatter)

        self.logger.addHandler(ch)
