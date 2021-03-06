package com.usthe.sureness.processor;

import com.usthe.sureness.processor.exception.SurenessAuthenticationException;
import com.usthe.sureness.processor.exception.SurenessAuthorizationException;
import com.usthe.sureness.subject.SubjectSum;
import com.usthe.sureness.subject.Subject;

/**
 * abstract processor
 * @author tomsun28
 * @date 12:28 2019-03-13
 */
public abstract class BaseProcessor implements Processor{

    /**
     * Determine whether this Processor supports the corresponding SubjectClass
     *
     * @param var subjectClass
     * @return support true, else false
     */
    @Override
    public abstract boolean canSupportSubjectClass(Class<?> var);

    /**
     * Get the subjectClass supported by this processor
     *
     * @return java.lang.Class? subjectClass
     */
    @Override
    public abstract Class<?> getSupportSubjectClass();

    @Override
    public SubjectSum process(Subject var) throws SurenessAuthenticationException, SurenessAuthorizationException {
        authorized(authenticated(var));
        return var.generateSubjectSummary();
    }
    /**
     * The interface that the authentication will call to complete the authentication
     * @param var subject
     * @return Subject subject
     * @throws SurenessAuthenticationException when authenticate error
     */
    public abstract Subject authenticated (Subject var) throws SurenessAuthenticationException;

    /**
     * The interface that the authorization will call, where the authorization is completed
     * @param var subject
     * @throws SurenessAuthorizationException when authorize error
     */
    public abstract void authorized(Subject var) throws SurenessAuthorizationException;
}
