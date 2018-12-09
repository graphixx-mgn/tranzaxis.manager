package codex.instance;

import codex.service.IService;


public interface IInstanceCommunicationService extends IService {

    @Override
    default String getTitle() {
        return "Instance Communication Service";
    }
    
}
