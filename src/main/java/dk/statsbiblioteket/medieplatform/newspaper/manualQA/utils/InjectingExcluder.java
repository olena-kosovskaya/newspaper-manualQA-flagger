package dk.statsbiblioteket.medieplatform.newspaper.manualQA.utils;

import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.AttributeParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.NodeBeginsParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.common.NodeEndParsingEvent;
import dk.statsbiblioteket.medieplatform.autonomous.iterator.eventhandlers.InjectingTreeEventHandler;

/**
 * An excluder for the injecting tree event handlers. Nessesary because the framework uses instance of to
 * determine if an eventhandler is injecting...
 */
public class InjectingExcluder extends InjectingTreeEventHandler {
    private String contains;
    private InjectingTreeEventHandler delegate;

    public InjectingExcluder(String contains, InjectingTreeEventHandler delegate) {
        this.contains = contains;
        this.delegate = delegate;
    }

    @Override
    public void handleNodeBegin(NodeBeginsParsingEvent event) {
        if (event.getName().contains(contains)) {
            return;
        }
        delegate.handleNodeBegin(event);
    }

    @Override
    public void handleNodeEnd(NodeEndParsingEvent event) {
        if (event.getName().contains(contains)) {
            return;
        }
        delegate.handleNodeEnd(event);
    }

    @Override
    public void handleAttribute(AttributeParsingEvent event) {
        if (event.getName().contains(contains)) {
            return;
        }
        delegate.handleAttribute(event);
    }

    @Override
    public void handleFinish() {
        delegate.handleFinish();
    }


}
