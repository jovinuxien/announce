"""Feed URI parameters: the source prefix the importer keys on (dba, tradera) and the run stamp."""
from datetime import datetime


def uri_params(params, spider):
    return {**params, "source": getattr(spider, "source", spider.name), "stamp": datetime.now().strftime("%Y%m%d%H%M")}
