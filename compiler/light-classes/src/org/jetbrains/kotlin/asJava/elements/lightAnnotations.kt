/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.asJava.elements

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.kotlin.asJava.LightClassGenerationSupport
import org.jetbrains.kotlin.asJava.classes.cannotModify
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaClassConstructorDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.declaresOrInheritsDefaultValue
import org.jetbrains.kotlin.resolve.source.getPsi

private val LOG = Logger.getInstance("#org.jetbrains.kotlin.asJava.elements.lightAnnotations")

abstract class KtLightAbstractAnnotation(parent: PsiElement, computeDelegate: () -> PsiAnnotation) :
    KtLightElementBase(parent), PsiAnnotation, KtLightElement<KtCallElement, PsiAnnotation> {

    private val _clsDelegate: PsiAnnotation by lazyPub(computeDelegate)

    override val clsDelegate: PsiAnnotation
        get() {
            if (this !is KtLightNonSourceAnnotation)
                throw Exception("KtLightAbstractAnnotation clsDelegate requested " + this.javaClass)
//            Exception("KtLightAbstractAnnotation clsDelegate requested " + this.javaClass).printStackTrace(System.out)
            return _clsDelegate
        }

    override fun getNameReferenceElement() = clsDelegate.nameReferenceElement

    override fun getOwner() = parent as? PsiAnnotationOwner

    override fun getMetaData() = clsDelegate.metaData

    override fun getParameterList() = clsDelegate.parameterList

    override fun canNavigate(): Boolean = super<KtLightElementBase>.canNavigate()

    override fun canNavigateToSource(): Boolean = super<KtLightElementBase>.canNavigateToSource()

    override fun navigate(requestFocus: Boolean) = super<KtLightElementBase>.navigate(requestFocus)

    open fun fqNameMatches(fqName: String): Boolean = qualifiedName == fqName
}

class KtLightAnnotationForSourceEntry(
        private val qualifiedName: String,
        override val kotlinOrigin: KtCallElement,
        parent: PsiElement,
        computeDelegate: () -> PsiAnnotation
) : KtLightAbstractAnnotation(parent, computeDelegate) {

    override fun getQualifiedName() = qualifiedName

    override fun getName() = null

    override fun findAttributeValue(name: String?) = getAttributeValue(name, true)

    override fun findDeclaredAttributeValue(name: String?): PsiAnnotationMemberValue? = getAttributeValue(name, false)

    private fun getAttributeValue(name: String?, useDefault: Boolean): PsiAnnotationMemberValue? {
        val name = name ?: run { throw Exception("null value call") }

        val resolvedCall = kotlinOrigin.getResolvedCall()!!
        val callEntry = resolvedCall.valueArguments.entries.find { (param, _) -> param.name.asString() == name } ?: return null

        val valueArguments = callEntry.value.arguments
        val valueArgument = valueArguments.firstOrNull()
        val argument = valueArgument?.getArgumentExpression()
        if (argument != null) {
            val arrayExpected = callEntry.key?.type?.let { KotlinBuiltIns.isArray(it) } ?: false

            if (arrayExpected && (argument is KtStringTemplateExpression || argument is KtConstantExpression || annotationName(argument) != null))
                return KtLightPsiArrayInitializerMemberValue(
                    PsiTreeUtil.findCommonParent(valueArguments.map { it.getArgumentExpression() }) as KtElement,
                    this,
                    { self -> valueArguments.mapNotNull { it.getArgumentExpression()?.let { ktExpressionAsAnnotationMember(self, it) } } })


            ktExpressionAsAnnotationMember(this, argument)?.let {
                return it
            }
        }

        if (useDefault && callEntry.key.declaresOrInheritsDefaultValue()) {
            val psiElement = callEntry.key.source.getPsi()
            when (psiElement) {
                is KtParameter ->
                    return psiElement.defaultValue?.let { ktExpressionAsAnnotationMember(this, it) }
                is PsiAnnotationMethod ->
                    return psiElement.defaultValue
            }
        }
        return null
    }


    override fun getNameReferenceElement(): PsiJavaCodeReferenceElement? {
        val reference = (kotlinOrigin as? KtAnnotationEntry)?.typeReference?.reference
                ?: (kotlinOrigin.calleeExpression as? KtNameReferenceExpression)?.reference
                ?: return null
        return KtLightPsiJavaCodeReferenceElement(
            kotlinOrigin.navigationElement,
            reference,
            { super.getNameReferenceElement()!! })
    }


    private val ktLightAnnotationParameterList by lazyPub { KtLightAnnotationParameterList() }

    override fun getParameterList(): PsiAnnotationParameterList = ktLightAnnotationParameterList

    inner class KtLightAnnotationParameterList() : KtLightElementBase(this),
        PsiAnnotationParameterList {
        override val kotlinOrigin get() = null

        private val _attributes: Array<PsiNameValuePair> by lazyPub {
            this@KtLightAnnotationForSourceEntry.kotlinOrigin.valueArguments.map { KtLightPsiNameValuePair(it as KtValueArgument) }
                .toTypedArray<PsiNameValuePair>()
        }

        override fun getAttributes(): Array<PsiNameValuePair> = _attributes

    }


    override fun delete() = kotlinOrigin.delete()

    override fun toString() = "@$qualifiedName"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other::class.java != this::class.java) return false
        return kotlinOrigin == (other as KtLightAnnotationForSourceEntry).kotlinOrigin
    }

    override fun hashCode() = kotlinOrigin.hashCode()

    override fun <T : PsiAnnotationMemberValue?> setDeclaredAttributeValue(attributeName: String?, value: T?) = cannotModify()
}

class KtLightNonSourceAnnotation(
    parent: PsiElement, clsDelegate: PsiAnnotation
) : KtLightAbstractAnnotation(parent, { clsDelegate }) {
    override val kotlinOrigin: KtAnnotationEntry? get() = null
    override fun getQualifiedName() = kotlinOrigin?.name ?: clsDelegate.qualifiedName
    override fun <T : PsiAnnotationMemberValue?> setDeclaredAttributeValue(attributeName: String?, value: T?) = cannotModify()
    override fun findAttributeValue(attributeName: String?) = clsDelegate.findAttributeValue(attributeName)
    override fun findDeclaredAttributeValue(attributeName: String?) = clsDelegate.findDeclaredAttributeValue(attributeName)
}

class KtLightNonExistentAnnotation(parent: KtLightElement<*, *>) : KtLightElementBase(parent), PsiAnnotation {
    override val kotlinOrigin get() = null
    override fun toString() = this.javaClass.name

    override fun <T : PsiAnnotationMemberValue?> setDeclaredAttributeValue(attributeName: String?, value: T?) = cannotModify()

    override fun getNameReferenceElement() = null
    override fun findAttributeValue(attributeName: String?) = null
    override fun getQualifiedName() = null
    override fun getOwner() = parent as? PsiAnnotationOwner
    override fun findDeclaredAttributeValue(attributeName: String?) = null
    override fun getMetaData() = null
    override fun getParameterList() = KtLightEmptyAnnotationParameterList(this)

    override fun canNavigate(): Boolean = super<KtLightElementBase>.canNavigate()

    override fun canNavigateToSource(): Boolean = super<KtLightElementBase>.canNavigateToSource()

    override fun navigate(requestFocus: Boolean) = super<KtLightElementBase>.navigate(requestFocus)
}

class KtLightEmptyAnnotationParameterList(parent: PsiElement) : KtLightElementBase(parent), PsiAnnotationParameterList {
    override val kotlinOrigin get() = null
    override fun getAttributes(): Array<PsiNameValuePair> = emptyArray()
}

class KtLightNullabilityAnnotation(member: KtLightElement<*, PsiModifierListOwner>, parent: PsiElement) : KtLightAbstractAnnotation(parent, {
    // searching for last because nullability annotations are generated after backend generates source annotations
    member.clsDelegate.modifierList?.annotations?.findLast {
        isNullabilityAnnotation(it.qualifiedName)
    } ?: KtLightNonExistentAnnotation(member)
}) {
    override fun fqNameMatches(fqName: String): Boolean {
        if (!isNullabilityAnnotation(fqName)) return false

        return super.fqNameMatches(fqName)
    }

    override val kotlinOrigin get() = null
    override fun <T : PsiAnnotationMemberValue?> setDeclaredAttributeValue(attributeName: String?, value: T?) = cannotModify()

    override fun findAttributeValue(attributeName: String?) = null

    override fun getQualifiedName(): String? = Nullable::class.java.name

    override fun getNameReferenceElement(): PsiJavaCodeReferenceElement? = null

    override fun findDeclaredAttributeValue(attributeName: String?) = null
}

internal fun isNullabilityAnnotation(qualifiedName: String?) = qualifiedName in backendNullabilityAnnotations

private val backendNullabilityAnnotations = arrayOf(Nullable::class.java.name, NotNull::class.java.name)

private fun KtElement.getResolvedCall(): ResolvedCall<out CallableDescriptor>? {
    val context = LightClassGenerationSupport.getInstance(this.project).analyze(this)
    return this.getResolvedCall(context)
}

fun ktExpressionAsAnnotationMember(lightParent: PsiElement, argument: KtExpression): PsiAnnotationMemberValue? {
    val argument = unwrapCall(argument)
    when (argument) {
        is KtStringTemplateExpression, is KtConstantExpression -> {
            println("processing KtLightPsiLiteral $argument [${argument.text}]")
            return KtLightPsiLiteral(argument, lightParent)
        }
        is KtCallExpression -> {
            val arguments = argument.valueArguments
            println("processing KtCallExpression $argument [${argument.text}] (${argument.calleeExpression}) KtCallExpression arguments:" + arguments)
            val annotationName = annotationName(argument.calleeExpression)
            if (annotationName != null) {
                return KtLightAnnotationForSourceEntry(annotationName, argument, lightParent, { TODO("not implemented") })
            }
            if (arguments.isNotEmpty())
                return KtLightPsiArrayInitializerMemberValue(
                    argument,
                    lightParent,
                    { self ->
                        arguments.mapNotNull {
                            it.getArgumentExpression()?.let { ktExpressionAsAnnotationMember(self, it) }
                        }
                    })
        }
        is KtCollectionLiteralExpression -> {
            val arguments = argument.getInnerExpressions()
            println("processing KtCollectionLiteralExpression $argument [${argument.text}] arguments:" + arguments)
            if (arguments.isNotEmpty())
                return KtLightPsiArrayInitializerMemberValue(
                    argument,
                    lightParent,
                    { self -> arguments.mapNotNull { ktExpressionAsAnnotationMember(self, it) } })
        }
    }
    return null
}

private fun getNameReference(callee: KtExpression?): KtNameReferenceExpression? {
    if (callee is KtConstructorCalleeExpression)
        return callee.constructorReferenceExpression as? KtNameReferenceExpression
    return callee as? KtNameReferenceExpression
}

private fun unwrapCall(callee: KtExpression): KtExpression {
    val callee = if (callee is KtDotQualifiedExpression) {
        callee.lastChild as? KtCallExpression ?: callee
    } else callee
    return callee
}

private fun annotationName(callee: KtExpression?): String? {
    val callee = callee?.let { unwrapCall(it) }

    if (callee is KtCallExpression) {
        val resultingDescriptor = callee.getResolvedCall()?.resultingDescriptor
        if (resultingDescriptor is JavaClassConstructorDescriptor && (resultingDescriptor.constructedClass.source.getPsi() as? PsiClass)?.isAnnotationType == true) {
            println("callee ${callee.text} is annotation")
            return (resultingDescriptor.constructedClass.source.getPsi() as? PsiClass)?.qualifiedName
        } else {
            println("callee ${callee.text} is not annotation")
        }
    }
    getNameReference(callee)?.references?.forEach {
        val resovledElement = it.resolve()
        when (resovledElement) {
            is PsiClass -> if (resovledElement.isAnnotationType == true)
                return resovledElement.qualifiedName
            is KtClass -> if (resovledElement.isAnnotation())
                return resovledElement.fqName?.toString()
            is KtConstructor<*> -> {
                val containingClassOrObject = resovledElement.getContainingClassOrObject()
                if (containingClassOrObject.isAnnotation())
                    return containingClassOrObject.fqName?.asString()
            }
        }
    }
    return null
}